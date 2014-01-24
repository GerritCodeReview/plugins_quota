This plugin allows to enforce quotas in Gerrit.

To protect a Gerrit installation it makes sense to limit the resources
that a project or group can consume. To do this a Gerrit administrator
can use this plugin to define quotas on project namespaces.

The following quotas are supported:

* Maximum number of projects in a namespace

The defined quotas are stored in a `quota.config` file in the
`refs/meta/config` branch of the `All-Project` root project.
Administrators can add and edit quotas by fetching this branch, editing
the `quota.config` file locally and pushing back the changes. The
`quota.config` file is a Git config file:

```
  [quota "sandbox/*"]
    maxRepos = 50
  [quota "public/*"]
    maxRepos = 100
```

<a id="maxRepos">
`quota.<namespace>.maxRepos`
: The maximum number of projects that can be created in this namespace.

A namespace can be specified as

* exact project name (`plugins/myPlugin`): Defines a quota for one project.

* as patterns (`sandbox/*`): Defines a quota for one project namespace.

* as for-each-pattern (`public/?/*`): Defines the same quota for each
subfolder.

Example: Allow the creation of 5 projects in each folder

```
  [quota "?/*"]
    maxRepos = 5
```

Example: Allow the creation of maximal 50 projects

```
  [quota "*"]
    maxRepos = 50
```

If a project name matches several quota namespaces the one quota
applies to the project that is defined first in the `quota.config`
file.

Example: Allow the creation of 10 projects in folder `test/*` and maximal
500 projects in total

```
  [quota "test/*"]
    maxRepos = 10
  [quota "*"]
    maxRepos = 500
```

Example: Allow the creation of 10 projects in folder `test/*` and 5
projects in each other folder

```
  [quota "test/*"]
    maxRepos = 10
  [quota "?/*"]
    maxRepos = 5
```
