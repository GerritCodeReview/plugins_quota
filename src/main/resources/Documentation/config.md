Configuration
=============

The @PLUGIN@ plugin supports the following quotas:

* Maximum number of projects in a namespace

The defined quotas are stored in a `quota.config` file in the
`refs/meta/config` branch of the `All-Projects` root project.
Administrators can add and edit quotas by fetching this branch, editing
the `quota.config` file locally and pushing back the changes. The
`quota.config` file is a Git config file:

```
  [quota "sandbox/*"]
    maxProjects = 50
  [quota "public/*"]
    maxProjects = 100
```

<a id="maxProjects">
`quota.<namespace>.maxProjects`
: The maximum number of projects that can be created in this namespace.

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
