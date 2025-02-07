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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.obolibrary.robot.CommandState;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDisjointClassesAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.model.parameters.Imports;

/**
 * A command to perform various “normalisation” steps on an ontology.
 */
public class NormalizeCommand extends BasePlugin {

    private List<String> baseIRIs = new ArrayList<>();

    public NormalizeCommand() {
        super("normalize", "apply some normalisation operations", "robot normalize [options]");

        options.addOption(null, "merge-axioms", true, "merge logically equivalent axioms");

        options.addOption(null, "inject-subset-declarations", true, "inject declarations for subset properties");
        options.addOption(null, "inject-synonym-declarations", true, "inject declarations for synonym types");
        options.addOption(null, "base-iri", true, "inject declaration for properties in the indicated namespace");

        options.addOption(null, "remove-dangling", true, "remove references to dangling classes");

        baseIRIs.add("http://purl.obolibrary.org/obo/");
        baseIRIs.add("http://www.ebi.ac.uk/efo/");
        baseIRIs.add("http://w3id.org/biolink/");
    }

    @Override
    public void performOperation(CommandState state, CommandLine line) throws Exception {
        if ( line.getOptionValue("merge-axioms", "false").equals("true") ) {
            mergeAxioms(state.getOntology());
        }

        boolean injectSubsetDecls = line.getOptionValue("inject-subset-declarations", "false").equals("true");
        boolean injectSynonymDecls = line.getOptionValue("inject-synonym-declarations", "false").equals("true");

        if ( injectSubsetDecls || injectSynonymDecls ) {
            injectDeclarations(state.getOntology(), injectSubsetDecls, injectSynonymDecls);
        }

        if ( line.getOptionValue("remove-dangling", "false").equals("true") ) {
            removeDangling(state.getOntology());
        }
    }

    private void mergeAxioms(OWLOntology ontology) {
        OWLOntologyManager mgr = ontology.getOWLOntologyManager();
        Set<OWLAxiom> origAxioms = ontology.getAxioms(Imports.EXCLUDED);
        Map<OWLAxiom, Set<OWLAnnotation>> annotsMap = new HashMap<>();

        for ( OWLAxiom ax : origAxioms ) {
            annotsMap.computeIfAbsent(ax.getAxiomWithoutAnnotations(), k -> new HashSet<>())
                    .addAll(ax.getAnnotations());
        }

        Set<OWLAxiom> mergedAxioms = new HashSet<>();
        for ( Map.Entry<OWLAxiom, Set<OWLAnnotation>> entry : annotsMap.entrySet() ) {
            mergedAxioms.add(entry.getKey().getAnnotatedAxiom(entry.getValue()));
        }

        mgr.removeAxioms(ontology, origAxioms);
        mgr.addAxioms(ontology, mergedAxioms);
    }

    private void injectDeclarations(OWLOntology ontology, boolean doSubsets, boolean doSynonyms) {
        Set<String> subsets = new HashSet<>();
        Set<String> synonyms = new HashSet<>();

        for ( OWLAxiom ax : ontology.getAxioms(Imports.EXCLUDED) ) {
            if ( ax instanceof OWLAnnotationAssertionAxiom ) {
                processAnnotation(((OWLAnnotationAssertionAxiom) ax).getAnnotation(), subsets, synonyms);
            }
            for ( OWLAnnotation annot : ax.getAnnotations() ) {
                processAnnotation(annot, subsets, synonyms);
            }
        }

        Set<OWLAxiom> newAxioms = new HashSet<>();
        OWLDataFactory factory = ontology.getOWLOntologyManager().getOWLDataFactory();
        if ( doSubsets ) {
            addDeclarationAxioms(newAxioms, factory, subsets,
                    factory.getOWLAnnotationProperty(Constants.SUBSET_PROPERTY));
        }
        if ( doSynonyms ) {
            addDeclarationAxioms(newAxioms, factory, synonyms,
                    factory.getOWLAnnotationProperty(Constants.SYNONYM_TYPE_PROPERTY));
        }

        if ( !newAxioms.isEmpty() ) {
            ontology.getOWLOntologyManager().addAxioms(ontology, newAxioms);
        }
    }

    private void addDeclarationAxioms(Set<OWLAxiom> axioms, OWLDataFactory factory, Set<String> iris,
            OWLAnnotationProperty parent) {
        for ( String iri : iris ) {
            axioms.add(factory.getOWLSubAnnotationPropertyOfAxiom(factory.getOWLAnnotationProperty(IRI.create(iri)),
                    parent));
        }
    }

    private void collectIRI(Set<String> collection, String value) {
        for ( String base : baseIRIs ) {
            if ( value.startsWith(base) ) {
                collection.add(value);
                return;
            }
        }
    }

    private void processAnnotation(OWLAnnotation annotation, Set<String> subsets, Set<String> synonyms) {
        if ( annotation.getValue().isIRI() ) {
            IRI propIRI = annotation.getProperty().getIRI();
            if ( propIRI.equals(Constants.IN_SUBSET) ) {
                collectIRI(subsets, annotation.getValue().asIRI().get().toString());
            } else if ( propIRI.equals(Constants.HAS_SYNONYM_TYPE) ) {
                collectIRI(synonyms, annotation.getValue().asIRI().get().toString());
            }
        }
    }

    private void removeDangling(OWLOntology ontology) {
        Set<OWLClass> dangling = new HashSet<>();
        for ( OWLClass klass : ontology.getClassesInSignature(Imports.INCLUDED) ) {
            if ( isDangling(ontology, klass) ) {
                dangling.add(klass);
            }
        }

        Set<OWLAxiom> axioms = new HashSet<>();
        for ( OWLAxiom ax : ontology.getAxioms(Imports.INCLUDED) ) {
            boolean remove = false;
            for ( OWLClass klass : ax.getClassesInSignature() ) {
                if ( dangling.contains(klass) ) {
                    remove = true;
                }
            }
            if ( remove ) {
                axioms.add(ax);
            }
        }

        ontology.getOWLOntologyManager().removeAxioms(ontology, axioms);
    }

    private boolean isDangling(OWLOntology ontology, OWLClass klass) {
        int nAxioms = 0;
        for ( OWLAxiom ax : ontology.getAxioms(klass, Imports.INCLUDED) ) {
            if ( ax instanceof OWLSubClassOfAxiom ) {
                OWLSubClassOfAxiom sca = (OWLSubClassOfAxiom) ax;
                if ( !sca.getSuperClass().isTopEntity() ) {
                    nAxioms += 1;
                }
            } else if ( !(ax instanceof OWLDisjointClassesAxiom) ) {
                nAxioms += 1;
            }
        }
        nAxioms += ontology.getAnnotationAssertionAxioms(klass.getIRI()).size();
        return nAxioms == 0;
    }
}
