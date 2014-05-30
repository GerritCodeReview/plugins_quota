Configuration
=============

Quota
-----

The defined quotas are stored in a `quota.config` file in the
`refs/meta/config` branch of the `All-Projects` root project.
Administrators can add and edit quotas by fetching this branch, editing
the `quota.config` file locally and pushing back the changes. The
`quota.config` file is a Git config file:

```
  [quota "sandbox/*"]
    maxProjects = 50
    maxRepoSize = 2 m
  [quota "public/*"]
    maxProjects = 100
    maxRepoSize = 10 m
  [quota "customerX/*"]
    maxProjects = 20
    maxTotalSize = 200 m
```

<a id="maxProjects">
`quota.<namespace>.maxProjects`
: The maximum number of projects that can be created in this namespace.

<a id="maxRepoSize">
`quota.<namespace>.maxRepoSize`
: The maximum total file size of a repository in this namespace. This is
the sum of sizes of all files in a Git repository where the size is
taken using the File.length() method. This means that, for example, a
reference file is counted as 41 bytes although it typically occupies a
block of 4K in the file system.

<a id="maxTotalSize">
`quota.<namespace>.maxTotalSize`
: The maximum total file size of all repositories in this namespace.
This is the sum of sizes of all files in all Git repositories in this
namespace.

If both "maxRepoSize" and "maxTotalSize" are defined in a quota section
then the more limiting quota will apply. For example, if the remaining
repository size of a repository (based on the "maxRepoSize" and
currently occupied space of that repository) is 2m and the remaining
total size (based on the "maxTotalSize" and currently occupied space of
all repositoris under that namespace) is 1m then the 1m is the remaining
size for that repository.

A namespace can be specified as

* exact project name (`plugins/myPlugin`): Defines a quota for one project.

* pattern (`sandbox/*`): Defines a quota for one project namespace.

* regular expression (`^test-.*/.*`): Defines a quota for the
namespace matching the regular expression.

* for-each-pattern (`?/*`): Defines the same quota for each
subfolder. `?` is a placeholder for any name and '?/*' with
'maxProjects = 3' means that for every subfolder 3 projects are
allowed. Hence '?/*' is a shortcut for having n explicit quotas:
  '<name1>/*' with 'maxProjects = 3'
  '<name2>/*' with 'maxProjects = 3'
  ...


If a project name matches several quota namespaces the one quota
applies to the project that is defined first in the `quota.config`
file.

Example: Allow the creation of 10 projects in folder `test/*` and maximal
500 projects in total

```
  [quota "test/*"]
    maxProjects = 10
  [quota "*"]
    maxProjects = 500
```

Example: Allow the creation of 10 projects in folder `test/*` and 5
projects in each other folder

```
  [quota "test/*"]
    maxProjects = 10
  [quota "?/*"]
    maxProjects = 5
```

Example: Allow the creation of 10 projects in folder 'test/*' and set
the quota of 2m for each of them
```
  [quota "test/*"]
    maxProjects = 10
    maxRepoSize = 2 m
```

Example: Allow the creation of 10 projects in folder 'test/*' and set
a quota of 20m for the total size of all repositories
```
  [quota "test/*"]
    maxProjects = 10
    maxTotalSize = 20 m
```

Example: Allow the creation of 10 projects in folder 'test/*' and set
a quota of 20m for the total size of all repositories. In addition make
sure that each individual repository cannot exceed 3m
```
  [quota "test/*"]
    maxProjects = 10
    maxRepoSize = 3 m
    maxTotalSize = 20 m
```

Publication Schedule
--------------------

Publication of repository sizes to registered UsageDataPublishedListeners
is configured in the `plugin.quota` subsection of the `gerrit.config` file.
The publication interval in minutes can be configured as `publicationInterval`.

Example: Publish repository sizes every hour
```
  [plugin "quota"]
    publicationInterval = 60
```

If no publicationInterval is configured, no data is published.
