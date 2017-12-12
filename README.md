metridoc-job
============

holds the job api and all jobs, see the [wiki](https://github.com/metridoc/metridoc-job/wiki) for more details

Installation
------------

### Requirements

* Java 7

### Recommended

* nix system
* MySQL database

### Installing MetriDoc Command Line Tool

In your bash-enabled terminal, type the following command:

```bash
curl -s https://raw.githubusercontent.com/metridoc/metridoc-job/master/install-mdoc.sh | sh
```

This will download and install the command line utility under **HOME/.metridoc/cli**. You can use the same command to upgrade metridoc, or you can also put the following into your **.bash_profile**:

```bash
function upgradeMetridoc {
	curl -s https://raw.githubusercontent.com/metridoc/metridoc-job/master/install-mdoc.sh | sh
}
```

After running these steps, the command line utility will be available in **metridoc-job-cli/build/install/mdoc**. Set your PATH variable to **build/install/mdoc/bin** and you will be ready to go.

To make sure mdoc is working simply type **mdoc** on the command line. You should get some usage information.

Connecting to your database
---------------------------

Frequently a metridoc job will connect to a database. Normally the connection params will come from **~/.metridoc/MetridocConfig.groovy**. The format for configuring a data source follows the grails [conventions](http://docs.grails.org/latest/guide/conf.html#dataSource). Below is an example of the content of a MetridocCondif file:

```Groovy
environments {

  development {
    dataSource {
      username = "sa"
      password = ""
      url = "jdbc:h2:mem:devDb;MVCC=TRUE;LOCK_TIMEOUT=10000"
      driverClassName = "org.h2.Driver"
    }
  }

  production {
    dataSource {
      username = "admin"
      password = ""
      url = "jdbc:h2:mem:productionDb;MVCC=TRUE;LOCK_TIMEOUT=10000"
      driverClassName = "org.h2.Driver"
    }
  }  
}
```