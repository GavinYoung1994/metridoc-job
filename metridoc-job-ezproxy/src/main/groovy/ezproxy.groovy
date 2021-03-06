/*
  *Copyright 2013 Trustees of the University of Pennsylvania. Licensed under the
  *	Educational Community License, Version 2.0 (the "License"); you may
  *	not use this file except in compliance with the License. You may
  *	obtain a copy of the License at
  *
  *http://www.osedu.org/licenses/ECL-2.0
  *
  *	Unless required by applicable law or agreed to in writing,
  *	software distributed under the License is distributed on an "AS IS"
  *	BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
  *	or implied. See the License for the specific language governing
  *	permissions and limitations under the License.
  */

import metridoc.core.services.ConfigService
import metridoc.ezproxy.entities.EzDoi
import metridoc.ezproxy.entities.EzDoiJournal
import metridoc.ezproxy.entities.EzHosts
import metridoc.ezproxy.services.CrossRefService
import metridoc.ezproxy.services.EzproxyDropTableService
import metridoc.ezproxy.services.EzproxyStepsService
import metridoc.ezproxy.services.EzproxyWireService
import metridoc.ezproxy.services.ResolveDoisService
import metridoc.service.gorm.GormService

includeService(ConfigService)
assert argsMap: "no arguments were provided, run [mdoc help ezproxy]"
assert argsMap.params: "a command must be provided, run [mdoc help ezproxy]"

def command = argsMap.params[0]
def commands = ["processHosts", "processDois", "resolveDois", "dropTables"]

if (!commands.contains(command)) {
    println ""
    println "  $command is not one of $commands, run [mdoc help ezproxy]"
    println ""
    System.exit(1)
}

switch (command) {
    case "processHosts":
        println "processing hosts"
        ingestFor(EzHosts)
        return
    case "processDois":
        println "processing dois"
        includeService(GormService).enableFor(EzDoiJournal, EzDoi)
        ingestFor(EzDoi)
        return
    case "resolveDois":
        println "resolving dois"
        includeService(GormService).enableFor(EzDoiJournal, EzDoi)
        includeService(CrossRefService)
        includeService(ResolveDoisService)
        runStep("resolveDois")
        return
    case "dropTables":
        println "dropping tables"
        includeService(EzproxyDropTableService)
        runStep("dropTables")
        return
}

void ingestFor(Class ezproxyIngestClass) {
    wireupServices(ezproxyIngestClass)
    if (argsMap.preview) {
        runStep("preview")
    }
    else {
        runStep("processEzproxyFile")
    }
}

EzproxyStepsService wireupServices(Class ezproxyIngestClass) {
    return includeService(EzproxyWireService).wireupServices(ezproxyIngestClass)
}