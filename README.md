Experimental ROBOT plugin
=========================

This project is intended to provide a playground to experiment with
[ROBOT](http://robot.obolibrary.org/) commands.

Commands implemented in this plugin may or may not be good ideas, and
may or may not be useful.

Currently available commands include:

* `checkpoint`, to set and revert to a “checkpoint” in a ROBOT pipeline;
* `expand-curies`, to expand CURIEs to IRIs in annotation values;
* `extract-orcids`, to inject [ORCIDIO](https://w3id.org/orcidio/orcidio.owl)
  individuals into an ontology.

Building and using
------------------
Build with Maven by running:

```sh
mvn clean package
```

This will produce two Jar files in the `target` directory.

The `exp.jar` file is the plugin itself, to be used with the standard
distribution of ROBOT (version >= 1.9.5). Place this file in your ROBOT
plugins directory (by default `~/.robot/plugins`), then call the
commands by prefixing them with the basename of the Jar file in the
plugins directory.

The `exp-robot-standalone-X.Y.Z.jar` file is a standalone version of
ROBOT that includes the commands provided by this plugin as is they were
built-in commands.

Copying
-------
This plugin is distributed under the terms of the GNU General Public
License, version 3 or higher. The full license is included in the
[COPYING file](COPYING) of the source distribution.
