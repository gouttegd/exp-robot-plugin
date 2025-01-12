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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.obolibrary.robot.CommandState;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.parameters.Imports;

/**
 * A command to merge logically equivalent axioms that only differ by their
 * annotations, collapsing all annotations onto a single axiom.
 */
public class MergeAxiomAnnotationsCommand extends BasePlugin {

    public MergeAxiomAnnotationsCommand() {
        super("merge-axiom-annotations", "Merge logically equivalent axioms", "robot merge-axiom-annotations");
    }

    @Override
    public void performOperation(CommandState state, CommandLine line) throws Exception {
        OWLOntology ont = state.getOntology();
        OWLOntologyManager mgr = ont.getOWLOntologyManager();

        Set<OWLAxiom> origAxioms = ont.getAxioms(Imports.EXCLUDED);
        Map<OWLAxiom, Set<OWLAnnotation>> annotsMap = new HashMap<OWLAxiom, Set<OWLAnnotation>>();

        for ( OWLAxiom ax : origAxioms ) {
            annotsMap.putIfAbsent(ax.getAxiomWithoutAnnotations(), new HashSet<OWLAnnotation>())
                    .addAll(ax.getAnnotations());
        }

        Set<OWLAxiom> mergedAxioms = new HashSet<OWLAxiom>();
        for ( OWLAxiom ax : annotsMap.keySet() ) {
            mergedAxioms.add(ax.getAnnotatedAxiom(annotsMap.get(ax)));
        }

        mgr.removeAxioms(ont, origAxioms);
        mgr.addAxioms(ont, mergedAxioms);
    }
}
