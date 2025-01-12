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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
 * A command to inject potentially missing SubPropertyOf axioms for properties
 * representing subsets and synonym types.
 */
public class InjectDeclarationsCommand extends BasePlugin {

    private List<String> baseIRIs = new ArrayList<String>();

    public InjectDeclarationsCommand() {
        super("inject-declarations", "Inject subset and synonym type declarations", "robot inject-declarations");
        options.addOption(null, "base-iri", true, "inject declarations for properties in the indicated namespace");

        baseIRIs.add("http://purl.obolibrary.org/obo/");
        baseIRIs.add("http://www.ebi.ac.uk/efo/");
        baseIRIs.add("http://w3id.org/biolink/");
    }

    @Override
    public void performOperation(CommandState state, CommandLine line) throws Exception {
        if (line.hasOption("base-iri")) {
            baseIRIs.add(line.getOptionValue("base-iri"));
        }

        OWLOntology ont = state.getOntology();
        OWLOntologyManager mgr = ont.getOWLOntologyManager();
        OWLDataFactory fac = mgr.getOWLDataFactory();

        Set<String> subsets = new HashSet<String>();
        Set<String> synonyms = new HashSet<String>();

        for ( OWLAxiom ax : ont.getAxioms(Imports.EXCLUDED) ) {
            if ( ax instanceof OWLAnnotationAssertionAxiom ) {
                processAnnotation(((OWLAnnotationAssertionAxiom) ax).getAnnotation(), subsets, synonyms);
            }
            for ( OWLAnnotation annot : ax.getAnnotations() ) {
                processAnnotation(annot, subsets, synonyms);
            }
        }

        Set<OWLAxiom> newAxioms = new HashSet<OWLAxiom>();
        for ( String subset : subsets ) {
            newAxioms.add(fac.getOWLSubAnnotationPropertyOfAxiom(fac.getOWLAnnotationProperty(IRI.create(subset)),
                    fac.getOWLAnnotationProperty(Constants.SUBSET_PROPERTY)));
        }
        for ( String synonym : synonyms ) {
            newAxioms.add(fac.getOWLSubAnnotationPropertyOfAxiom(fac.getOWLAnnotationProperty(IRI.create(synonym)),
                    fac.getOWLAnnotationProperty(Constants.SYNONYM_TYPE_PROPERTY)));
        }

        mgr.addAxioms(ont, newAxioms);
    }

    private boolean isInBase(String value) {
        for ( String base : baseIRIs ) {
            if ( value.startsWith(base) ) {
                return true;
            }
        }
        return false;
    }

    private void processAnnotation(OWLAnnotation annotation, Set<String> subsets, Set<String> synonyms) {
        if ( annotation.getValue().isIRI() ) {
            String value = annotation.getValue().asIRI().get().toString();
            if ( isInBase(value) ) {
                IRI propIRI = annotation.getProperty().getIRI();
                if ( propIRI.equals(Constants.IN_SUBSET) ) {
                    subsets.add(value);
                } else if ( propIRI.equals(Constants.HAS_SYNONYM_TYPE) ) {
                    synonyms.add(value);
                }
            }
        }
    }

}
