/*
 * Experimental ROBOT plugin
 * Copyright © 2025 Damien Goutte-Gattat
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.incenp.obofoundry.robot;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.obolibrary.robot.CommandLineHelper;
import org.obolibrary.robot.CommandState;
import org.obolibrary.robot.MergeOperation;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationValue;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A command to check an ontology against an “upper ontology”, that is, to check
 * that class within the current ontology are subclasses of one of the classes
 * of the upper ontology.
 */
public class ValidateCommand extends BasePlugin {

    private static final Logger logger = LoggerFactory.getLogger(ValidateCommand.class);

    private List<String> baseIRIs = new ArrayList<>();

    public ValidateCommand() {
        super("validate", "validate alignment with an upper ontology",
                "robot validate [--upper-ontology[-iri] ONT] [--report-output FILE");

        options.addOption("u", "upper-ontology", true, "load upper ontology from specified file");
        options.addOption("U", "upper-ontology-iri", true, "load upper ontology from specified IRI");

        options.addOption("b", "base-iri", true, "only check classes in the specified namespace");
        options.addOption("d", "ignore-dangling", true, "if true, ignore dangling classes");

        options.addOption("r", "reasoner", true, "reasoner to use");
        options.addOption("O", "report-output", true, "write report to the specified file");
    }

    @Override
    public void performOperation(CommandState state, CommandLine line) throws Exception {
        boolean ignoreDangling = line.getOptionValue("ignore-dangling", "false").equals("true");
        if ( line.hasOption("base-iri") ) {
            for ( String iri : line.getOptionValues("base-iri") ) {
                baseIRIs.add(iri);
            }
        }

        OWLOntology upperOntology = null;
        if ( line.hasOption("upper-ontology") ) {
            upperOntology = ioHelper.loadOntology(line.getOptionValue("upper-ontology"), true);
        } else if ( line.hasOption("upper-ontology-iri") ) {
            String arg = line.getOptionValue("upper-ontology-iri");
            upperOntology = ioHelper.loadOntology(getIRI(arg, "upper-ontology-iri"));
        } else {
            logger.warn("No upper ontology specified, assuming COB is meant");
            upperOntology = ioHelper.loadOntology(IRI.create("http://purl.obolibrary.org/obo/cob.owl"));
        }

        // The classes we need to check against
        Set<OWLClass> upperClasses = upperOntology.getClassesInSignature(Imports.INCLUDED);
        upperClasses.remove(upperOntology.getOWLOntologyManager().getOWLDataFactory().getOWLThing());

        // We merge the current ontology into the upper ontology rather than the other
        // way around, so that the current ontology remains unchanged and can be used
        // for other operations downstream in the ROBOT pipeline
        MergeOperation.mergeInto(state.getOntology(), upperOntology, true, true);
        OWLReasoner reasoner = CommandLineHelper.getReasonerFactory(line).createReasoner(upperOntology);

        Set<OWLClass> unalignedClasses = new HashSet<>();
        for ( OWLClass klass : upperOntology.getClassesInSignature(Imports.INCLUDED) ) {
            if ( !klass.isTopEntity() && !upperClasses.contains(klass) && isInBase(klass.getIRI().toString()) ) {
                if ( ignoreDangling && isDangling(upperOntology, klass) ) {
                    continue;
                }
                if ( isObsolete(upperOntology, klass) ) {
                    continue;
                }

                Set<OWLClass> ancestors = reasoner.getSuperClasses(klass, false).getFlattened();
                boolean aligned = false;
                for ( OWLClass upperClass : upperClasses ) {
                    if ( ancestors.contains(upperClass) ) {
                        aligned = true;
                        break;
                    }
                }
                if ( !aligned ) {
                    // Report only top-level classes (whose only parent is owl:Thing)
                    if ( ancestors.size() == 1 ) {
                        unalignedClasses.add(klass);
                    }
                }
            }
        }

        if ( line.hasOption("report-output") ) {
            BufferedWriter writer = new BufferedWriter(new FileWriter(line.getOptionValue("report-output")));
            List<String> unalignedIRIs = new ArrayList<>();
            for ( OWLClass unalignedClass : unalignedClasses ) {
                unalignedIRIs.add(unalignedClass.getIRI().toString());
            }
            unalignedIRIs.sort((a, b) -> a.compareTo(b));
            for ( String iri : unalignedIRIs ) {
                writer.write(iri);
                writer.write('\n');
            }
            writer.close();
        }

        if ( !unalignedClasses.isEmpty() ) {
            logger.error("Ontology contains {} top-level unaligned class(es)", unalignedClasses.size());
            System.exit(1);
        }
    }

    /*
     * Checks whether the given IRI is in one of the “base” namespaces; if no base
     * namespace has been declared, treat all IRIs as being in scope.
     */
    private boolean isInBase(String iri) {
        for ( String base : baseIRIs ) {
            if ( iri.startsWith(base) ) {
                return true;
            }
        }
        return baseIRIs.isEmpty();
    }

    /*
     * Checks whether a class is dangling. For the purpose of this command, a class
     * is considered to be dangling if the ontology contains no defining axioms and
     * no annotation assertion axioms about it.
     */
    private boolean isDangling(OWLOntology ontology, OWLClass klass) {
        return (ontology.getAxioms(klass, Imports.INCLUDED).isEmpty()
                && ontology.getAnnotationAssertionAxioms(klass.getIRI()).isEmpty());
    }

    /*
     * Checks whether a class is obsolete. We systematically exclude obsolete
     * classes from the alignment check, as obsoletion SOPs typically recommend to
     * remove all defining axioms when obsoleting a class, meaning the obsolete
     * classes will end up directly under owl:Thing and cannot possibly be aligned
     * with any upper ontology; this is normal and should not be flagged as an
     * error.
     */
    private boolean isObsolete(OWLOntology ontology, OWLClass klass) {
        for ( OWLAnnotationAssertionAxiom ax : ontology.getAnnotationAssertionAxioms(klass.getIRI()) ) {
            if ( ax.getProperty().isDeprecated() ) {
                OWLAnnotationValue value = ax.getValue();
                if ( value.isLiteral() ) {
                    OWLLiteral litValue = value.asLiteral().get();
                    if ( litValue.isBoolean() && litValue.getLiteral().equals("true") ) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
