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
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.RemoveOntologyAnnotation;

/**
 * A command to post-process a ODK import module. Post-processing consists in
 * stripping all ontology annotations from the module, except {@code dc:source}.
 */
public class PostprocessODKImportCommand extends BasePlugin {

    public PostprocessODKImportCommand() {
        super("postprocess-odk-import", "Postprocess a ODK import module", "robot postprocess-odk-import");
    }

    @Override
    public void performOperation(CommandState state, CommandLine line) throws Exception {
        OWLOntology ont = state.getOntology();
        List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>();
        for ( OWLAnnotation annot : ont.getAnnotations() ) {
            IRI propIRI = annot.getProperty().getIRI();
            if ( !propIRI.equals(Constants.DC_SOURCE) ) {
                changes.add(new RemoveOntologyAnnotation(ont, annot));
            }
        }
        ont.getOWLOntologyManager().applyChanges(changes);
    }

}
