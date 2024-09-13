/*
 * Experimental ROBOT plugin
 * Copyright © 2024 Damien Goutte-Gattat
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
 * You should have received a copy of the Gnu General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.incenp.obofoundry.robot;

import java.util.HashSet;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.obolibrary.robot.CommandState;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.parameters.Imports;

/**
 * A command to inject ORCID individuals into an ontology.
 * <p>
 * That command will find all references to ORCID individuals in annotations of
 * the current ontology, then extract from ORCIDIO all the axioms that define
 * the referenced individuals.
 */
public class ExtractOrcidCommand extends BasePlugin {

    private final static IRI CONTRIBUTOR = IRI.create("http://purl.org/dc/terms/contributor");

    public ExtractOrcidCommand() {
        super("extract-orcids", "extract ORCIDs referenced in the ontology",
                "robot extract-orcids -i <INPUT> [--orcid-file <FILE>] [--orcid-module <FILE>]");

        options.addOption(null, "orcid-file", true, "Extract ORCIDs from the specified ontology");
        options.addOption(null, "orcid-module", true, "Save extracted ORCIDs to the specified file");
        options.addOption(null, "merge", false, "Merge the extracted ORCIDs into the current ontology");
        options.addOption(null, "property", true,
                "Extract ORCIDS referenced in annotations with the specified property");
    }

    @Override
    public void performOperation(CommandState state, CommandLine line) throws Exception {
        OWLOntology source = state.getOntology();
        OWLOntologyManager mgr = source.getOWLOntologyManager();
        OWLDataFactory factory = mgr.getOWLDataFactory();

        HashSet<IRI> properties = new HashSet<IRI>();
        if ( line.hasOption("property") ) {
            for ( String p : line.getOptionValues("property") ) {
                properties.add(getIRI(p, "property"));
            }
        } else {
            properties.add(CONTRIBUTOR);
        }

        // Collect all IRIs used in annotations with the target properties
        HashSet<IRI> refs = new HashSet<IRI>();
        for ( OWLAnnotation annot : source.getAnnotations() ) {
            processAnnotation(annot, refs, properties);
        }
        for (OWLAxiom ax : source.getAxioms(Imports.INCLUDED)) {
            if ( ax instanceof OWLAnnotationAssertionAxiom ) {
                processAnnotation(((OWLAnnotationAssertionAxiom) ax).getAnnotation(), refs, properties);
            }
            for ( OWLAnnotation annot : ax.getAnnotations() ) {
                processAnnotation(annot, refs, properties);
            }
        }

        // Get the referenced ORCID individuals
        OWLOntology orcidOnt = null;
        if ( line.hasOption("orcid-file") ) {
            // Get them from an external file
            orcidOnt = ioHelper.loadOntology(line.getOptionValue("orcid-file"));
        } else {
            // Get them directly from the current ontology (assuming ORCIDIO has already
            // been merged in at this point)
            orcidOnt = source;
        }
        HashSet<OWLAxiom> axioms = new HashSet<OWLAxiom>();
        for ( IRI ref : refs ) {
            if ( orcidOnt.containsIndividualInSignature(ref) ) {
                axioms.addAll(orcidOnt.getAxioms(factory.getOWLNamedIndividual(ref), Imports.INCLUDED));
                axioms.addAll(orcidOnt.getAnnotationAssertionAxioms(ref));
            }
        }

        // Save the result
        if ( line.hasOption("orcid-module") ) {
            // Save to a separate file
            OWLOntology output = mgr.createOntology();
            mgr.addAxioms(output, axioms);
            ioHelper.saveOntology(output, line.getOptionValue("orcid-module"));
        }
        if ( line.hasOption("merge") ) {
            // Merge into the current ontology; only makes sense with --orcid-file,
            // otherwise the current ontology already contains all ORCIDIO axioms
            mgr.addAxioms(source, axioms);
        }
    }

    private void processAnnotation(OWLAnnotation annotation, Set<IRI> refs, Set<IRI> properties) {
        if ( properties.contains(annotation.getProperty().getIRI()) ) {
            if ( annotation.getValue().isIRI() ) {
                refs.add(annotation.getValue().asIRI().get());
            } else if ( annotation.getValue().isLiteral() ) {
                refs.add(IRI.create(annotation.getValue().asLiteral().get().getLiteral()));
            }
        }
    }
}
