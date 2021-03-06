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

package metridoc.cli

import groovy.io.FileType
import groovy.util.logging.Slf4j
import org.apache.commons.lang.SystemUtils
import org.slf4j.LoggerFactory

import static metridoc.cli.MetridocMain.LONG_JOB_PREFIX

/**
 * Created with IntelliJ IDEA on 10/25/13
 * @author Tommy Barker
 */
@Slf4j
class RunJobCommand implements Command {
    def lastResult
    MetridocMain main

    @Override
    synchronized boolean run(OptionAccessor options) {
        def arguments = options.arguments()
        def shortJobName = arguments[0]

        def file
        if (isUrl(shortJobName)) {
            def slashIndex = shortJobName.lastIndexOf("/")
            def questionIndex = shortJobName.lastIndexOf(".groovy")
            def fileName
            if (questionIndex > 0) {
                fileName = shortJobName.substring(slashIndex + 1, questionIndex)
            }
            else {
                fileName = shortJobName.substring(slashIndex + 1)
            }

            try {
                file = File.createTempFile(fileName, ".groovy")
            }
            catch (IOException ignored) {
                def tmpDir = new File("$main.home${MetridocMain.slash}.metridoc${MetridocMain.slash}tmp")
                if(!tmpDir.exists()) {
                    assert tmpDir.mkdirs() : "Could not create $tmpDir"
                }
                file = new File(tmpDir, "${fileName}.groovy")
                file.createNewFile()
            }

            file.setText(new URL(shortJobName).text, "utf-8")
            file.deleteOnExit()
        }

        file = file ?: new File(shortJobName)
        File metridocScript
        def loader = findHighestLevelClassLoader()
        addLibDirectories(loader)

        if (file.isFile()) {
            metridocScript = file
            def parentFile  = metridocScript.absoluteFile.parentFile
            if (parentFile) {
                addDirectoryResourcesToClassPath(this.class.classLoader as URLClassLoader, parentFile)
            }
        }
        else if (file.isDirectory()) {
            log.info "checking if [$file.canonicalPath] has mdoc directory"
            def mdoc = ImportJobsCommand.getMdoc(file)
            if (mdoc.exists()) {
                addImportsToClassPath(this.class.classLoader as URLClassLoader, mdoc)
            }
            addDirectoryResourcesToClassPath(this.class.classLoader as URLClassLoader, file)
            metridocScript = getRootScriptFromDirectory(file)
        }
        else {
            log.info "checking if [$file.canonicalPath] has mdoc directory"
            def jobDir = main.getJobDir(shortJobName)
            def mdoc = ImportJobsCommand.getMdoc(jobDir)
            if (mdoc.exists()) {
                addImportsToClassPath(this.class.classLoader as URLClassLoader, mdoc)
            }
            addDirectoryResourcesToClassPath(this.class.classLoader as URLClassLoader, jobDir)
            metridocScript = getRootScriptFromDirectory(jobDir, shortJobName)
        }

        def binding = new Binding()

        binding.args = [] as String[]
        def log = LoggerFactory.getLogger(this.getClass())
        log.debug "parsing arguments $arguments to be used with job $shortJobName"
        def argsList = main.args as List
        def index = argsList.indexOf(arguments[0])
        def jobArgsList = argsList[(index + 1)..<argsList.size()]
        log.debug "arguments used in job $shortJobName after removing job name are $jobArgsList"
        binding.args = jobArgsList as String[]

        if (options.stacktrace) {
            binding.stacktrace = true
        }

        assert metridocScript && metridocScript.exists(): "root script does not exist"
        def thread = Thread.currentThread()

        def shell = new GroovyShell(thread.contextClassLoader, binding)
        thread.contextClassLoader = shell.classLoader
        log.info "Running $metridocScript at ${new Date()}"
        def response = null
        def throwable
        try {
            response = shell.evaluate(metridocScript)
        }
        catch (Throwable badExecution) {
            throwable = badExecution
        }
        log.info "Finished running $metridocScript at ${new Date()}"
        if (throwable) throw throwable
        lastResult = response
        return true
    }

    protected static boolean isUrl(String possibleUrl) {
        try {
            new URL(possibleUrl).toURI()
            return true
        }
        catch (Throwable ignore) {
            return false
        }
    }

    private URLClassLoader findHighestLevelClassLoader() {
        def loader = this.class.classLoader

        if (loader.rootLoader) {
            return this.class.classLoader.rootLoader as URLClassLoader
        }

        def loaders = []
        loaders << loader
        while (loader.parent) {
            loaders << loader.parent
            loader = loader.parent
        }
        loaders = loaders.reverse()

        for (it in loaders) {
            if (it instanceof URLClassLoader) {
                return it
            }
        }

        throw new RuntimeException("Could not find a suitable classloader")
    }

    protected static void addDirectoryResourcesToClassPath(URLClassLoader loader, File file) {
        def resourceDir = new File(file, "src${MetridocMain.slash}main${MetridocMain.slash}resources")
        if (resourceDir.exists()) {
            loader.addURL(resourceDir.toURI().toURL())
        }
        def groovyDir = new File(file, "src${MetridocMain.slash}main${MetridocMain.slash}groovy")
        if (groovyDir.exists()) {
            loader.addURL(groovyDir.toURI().toURL())
        }

        loader.addURL(file.toURI().toURL())
    }

    protected static void addImportsToClassPath(URLClassLoader loader, File mdoc) {
        mdoc.eachDir {
            log.info "adding directory [${it}] to classpath"
            addDirectoryResourcesToClassPath(loader, it)
        }
    }

    protected File getRootScriptFromDirectory(File directory, String shortName = null) {
        if (shortName == null) {
            def path = directory.canonicalPath
            def index = path.lastIndexOf(SystemUtils.FILE_SEPARATOR)
            shortName = getShortName(path.substring(index + 1))
        }

        def response

        response = getFileFromDirectory(directory, "metridoc.groovy")
        if (response) return response

        response = getFileFromDirectory(directory, "${shortName}.groovy")
        if (response) return response

        return response
    }

    protected static String getShortName(String longJobName) {
        if (longJobName.startsWith(LONG_JOB_PREFIX)) {
            def shortName = longJobName.substring(LONG_JOB_PREFIX.size())
            def index = shortName.lastIndexOf("-")
            if (index != -1) {
                return shortName.substring(0, index)
            }

            return shortName
        }
        return longJobName
    }

    protected static File getFileFromDirectory(File directory, String fileName) {

        def response

        response = new File(directory, fileName)
        if (response.exists()) {
            return response
        }

        def groovyDir = new File(directory, "src${MetridocMain.slash}main${MetridocMain.slash}groovy")
        if (groovyDir.exists()) {
            response = new File(groovyDir, fileName)
            if (response.exists()) {
                return response
            }
        }

        def resourcesDir = new File(directory, "src${MetridocMain.slash}main${MetridocMain.slash}resources")
        if (resourcesDir.exists()) {
            response = new File(resourcesDir, fileName)
            if (response.exists()) {
                return response
            }
        }

        return null
    }

    void addLibDirectories(URLClassLoader classLoader) {
        main.libDirectories.each {
            addJarsFromDirectory(classLoader, new File(it))
        }
    }

    static void addJarsFromDirectory(URLClassLoader classloader, File directory) {
        if (directory.exists() && directory.isDirectory()) {
            directory.eachFile(FileType.FILES) {
                if (it.name.endsWith(".jar")) {
                    classloader.addURL(it.toURI().toURL())
                }
            }
        }
    }
}
