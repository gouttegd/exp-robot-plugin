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

import org.apache.commons.cli.CommandLine;
import org.obolibrary.robot.CommandState;
import org.semanticweb.owlapi.model.AddOntologyAnnotation;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;

/**
 * A command to pre-process a ODK import module. Preprocessing, at least for
 * now, consists simply in adding a {@code dc:source} annotation to the module,
 * derived from its version IRI.
 */
public class PreprocessODKImportCommand extends BasePlugin {

    public PreprocessODKImportCommand() {
        super("preprocess-odk-import", "Preprocess a ODK import module", "robot preprocess-odk-import");
    }

    @Override
    public void performOperation(CommandState state, CommandLine line) throws Exception {
        OWLOntology ont = state.getOntology();
        IRI versionIRI = ont.getOntologyID().getVersionIRI().orNull();
        if ( versionIRI != null ) {
            OWLOntologyManager mgr = ont.getOWLOntologyManager();
            OWLDataFactory fac = mgr.getOWLDataFactory();
            AddOntologyAnnotation addSource = new AddOntologyAnnotation(ont,
                    fac.getOWLAnnotation(fac.getOWLAnnotationProperty(Constants.DC_SOURCE), versionIRI));
            mgr.applyChange(addSource);
        }
    }

}
