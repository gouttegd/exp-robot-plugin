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
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.obolibrary.robot.CommandState;
import org.semanticweb.owlapi.model.AddOntologyAnnotation;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.RemoveOntologyAnnotation;

/**
 * A command to manipulate the ontology annotations.
 * <p>
 * This could probably go into the standard {@code annotate} command itself.
 */
public class AnnotateCommand extends BasePlugin {

    public AnnotateCommand() {
        super("annotate", "edit ontology annotations", "robot annotate [--add-source] [--remove-all]");

        options.addOption("s", "add-source", true, "add a dc:source annotation based on the ontology version IRI");
        options.addOption("r", "remove-all", true, "remove all ontology annotations");

    }

    @Override
    public void performOperation(CommandState state, CommandLine line) throws Exception {
        OWLOntology ontology = state.getOntology();
        OWLOntologyManager mgr = ontology.getOWLOntologyManager();
        OWLDataFactory fac = mgr.getOWLDataFactory();
        List<OWLOntologyChange> changes = new ArrayList<>();

        if ( line.getOptionValue("remove-all", "false").equals("true") ) {
            for ( OWLAnnotation annot : ontology.getAnnotations() ) {
                changes.add(new RemoveOntologyAnnotation(ontology, annot));
            }
        }

        if ( line.getOptionValue("add-source", "false").equals("true") ) {
            IRI versionIRI = ontology.getOntologyID().getVersionIRI().orNull();
            if ( versionIRI != null ) {
                changes.add(new AddOntologyAnnotation(ontology,
                        fac.getOWLAnnotation(fac.getOWLAnnotationProperty(Constants.DC_SOURCE), versionIRI)));
            }
        }

        if ( !changes.isEmpty() ) {
            mgr.applyChanges(changes);
        }
    }

}
