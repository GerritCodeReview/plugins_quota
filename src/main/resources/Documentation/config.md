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

<a id="maxProjects" />
`quota.<namespace>.maxProjects`
: The maximum number of projects that can be created in this namespace.

<a id="maxRepoSize" />
`quota.<namespace>.maxRepoSize`
: The maximum total file size of a repository in this namespace. This is
the sum of sizes of all files in a Git repository where the size is
taken using the File.length() method. This means that, for example, a
reference file is counted as 41 bytes although it typically occupies a
block of 4K in the file system.

<a id="maxTotalSize" />
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
  subfolder. `?` is a placeholder for any name and `?/*` with
  'maxProjects = 3' means that for every subfolder 3 projects are
  allowed. Hence `?/*` is a shortcut for having n explicit quotas:<br />
  `<name1>/*` with 'maxProjects = 3'<br />
  `<name2>/*` with 'maxProjects = 3'<br />
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

Example: Allow the creation of 10 projects in folder `test/*` and set
the quota of 2m for each of them

```
  [quota "test/*"]
    maxProjects = 10
    maxRepoSize = 2 m
```

Example: Allow the creation of 10 projects in folder `test/*` and set
a quota of 20m for the total size of all repositories

```
  [quota "test/*"]
    maxProjects = 10
    maxTotalSize = 20 m
```

Example: Allow the creation of 10 projects in folder `test/*` and set
a quota of 20m for the total size of all repositories. In addition make
sure that each individual repository cannot exceed 3m

```
  [quota "test/*"]
    maxProjects = 10
    maxRepoSize = 3 m
    maxTotalSize = 20 m
```

One could also add quotas in global section that would be applicable to all
the projects (including the namespaces that are already defined).

```
  [global]
    maxProjects = 500
    maxRepoSize = 300 m
    maxTotalSize = 200 m
```

If one prefers computing a repository size by adding the size of the git objects,
the following section should be added into the `gerrit.config` file:

```
  [plugin "quota"]
    useGitObjectCount = true
```

<a id="useGitObjectCount" />
`plugin.quota.useGitObjectCount`
: Use git object count. If true, *repoSize = looseObjectsSize +
packedObjectsSize*, where *looseObjectsSize* and *packedObjectsSize* are given
by JGit RepoStatistics. By default, false.

Rate Limits
-----------

The defined rate limits are stored in the `quota.config` file in the
`refs/meta/config` branch of the `All-Projects` root project. Rate
limits are defined per user group and rate limit type.

Example:

```
  [group "buildserver"]
    uploadpack = 10 / min burst 500

  [group "app"]
    restapi = 12 / min burst 60

  [group "Registered Users"]
    uploadpack = 1 /min burst 180

  [group "Anonymous Users"]
    uploadpack = 6/h burst 12
    restapi = 30/m burst 200
```

For logged in users rate limits are associated to their accountId. For
anonymous users rate limits are associated to their remote host address.
If multiple anonymous users are accessing Gerrit via the same host (e.g.
a proxy) they share a common rate limit.

If a user is a member of multiple groups mentioned in `quota.config`
the limit applies that is defined first in the `quota.config` file.
This resolves ambiguity in case the user is a member of multiple groups
used in the configuration. Note, all users are members of "Anonymous Users".

Use group "Anonymous Users" to define the rate limit for anonymous users.
Use group "Registered Users" to define the default rate limit for all logged
in users.

Rate limits can also be specified in the global section; these limits are
always applied, regardless of group matching. If a user matches a group, the
limits from the first matching group are applied in addition to the global
limits.

Format of the rate limit entries in `quota.config`:

```
  [group "<groupName>"]
    <rateLimitType> = <rateLimit> <rateUnit> burst <storedRequests>
```

The group can be defined by its name or UUID.

<a id="rateLimitType" />
`group.<groupName>.<rateLimitType>`
: identifies which request type is limited by this configuration.
The following rate limit types are supported:
* `uploadpack`: rate limit for uploadpack (fetch) requests
for the given group
* `restapi`: rate limit for REST API requests

<a id="rateLimit" />
`group.<groupName>.<rateLimit>`
: The rate limit (first parameter) defines the maximum allowed request rate.

<a id="rateUnit" />
`group.<groupName>.<rateUnit>`
: Rate limits can be defined using the following rate units:<br />
`/s`, `/sec`, `/second`: requests per second<br />
`/m`, `/min`, `/minute`: requests per minute<br />
`/h`, `/hr`, `/hour`: requests per hour<br />
`/d`, `/day`: requests per day<br />

<a id="burst" />
`group.<groupName>.<storedRequests>`
: The `burst` parameter allows to define how many unused requests can be
stored for later use during idle times. This allows clients to send
bursts of requests exceeding their rate limit until all their stored
requests are consumed. For `restapi`, `burst` requests can be served
at the very beginning of a client interaction with the back-end server,
as if idle time would already have been accumulated.

If a rate limit configuration value is invalid or missing for a group,
that value is ignored and a warning is logged.

Example:

Configure a rate limit of maximum 30 fetch request per hour for
the group of registered users. Up to 60 unused requests can be stored
during idle times which may be consumed at a later time to send bursts
of requests above the maximum request rate.

```
  [group "Registered Users"]
    uploadpack = 30/hour burst 60
```

The rate limit exceeded message can be configured.

For `uploadpack`, by setting parameter
`uploadpackLimitExceededMsg` in the `plugin.quota` subsection of the
`gerrit.config` file. `${rateLimit}` token is supported in the message and
will be replaced by effective rate limit per hour.
Defaults to `Exceeded rate limit of ${rateLimit} fetch requests/hour` .

For `restapi`, configure the message by setting the parameter
`restapiLimitExceededMsg` in the `plugin.quota` subsection of the
`gerrit.config` file. `${rateLimit}` and `${burstsLimit}` tokens
are supported in the message and will be replaced by the effective rate
limit per hour and the effective number of burst permits, correspondingly.
The default message reads:
`Exceeded rate limit of ${rateLimit} REST API requests/hour (or idle `
`time used up in bursts of max ${burstsLimit} requests)` .

<a id="maxConcurrentRestApiCallsPerUser" />
`maxConcurrentRestApiCallsPerUser`

Eventhough we have ratelimitng over a window of period, costly restapis
ran concurrently by users could still bring down the server. Using the
below config we could limit the concurrent calls.

```
  [group "Registered Users"]
    maxConcurrentRestApiCallsPerUser = 20
```

Note that this config excepts an integer as value rather than the other
format explained above.

Task Quota
-----------

Task quotas provide fine-grained control over queues for administrators.
Once the defined limit is reached, any additional tasks are parked, preventing
them from consuming threads and allowing other tasks to continue execution.

The `maxStartForTaskForQueue` setting defines the maximum number of threads
that can be started for a specific task and queue combination. Example:

```
  [quota "*"]
    maxStartForTaskForQueue = 20 uploadpack SSH-Interactive-Worker
    maxStartForTaskForQueue = 10 uploadpack SSH-Batch-Worker
```

Queue names can be found at `GET /config/server/tasks/ HTTP/1.0`

Additionally, to scope the user use `maxStartForTaskForUserForQueue`

```
  [quota "*"]
    maxStartForTaskForUserForQueue = 20 uploadpack userA SSH-Interactive-Worker
    maxStartForTaskForUserForQueue = 10 uploadpack userB SSH-Batch-Worker
```

or to make it applicable for every user:

```
  [quota "*"]
    maxStartPerUserForTaskForQueue = 20 uploadpack SSH-Interactive-Worker
```

Currently supported tasks:

* `uploadpack`: Maps directly to git-upload-pack operations (used during Git
  fetches or clones).
* `receivepack`: Maps directly to git-receive-pack operations (used during Git
  pushes).

The `softMaxStartPerUserForQueue` setting defines a soft maximum number of threads
per user that should be started for a specific task and queue combination. Unlike
the `maxStartPerUserForTaskForQueue`, a softMax will allow a user to start more tasks
if the server has more than one idle thread. This helps maintain a high level of
interactive responsiveness without dedicating too many threads which would likely
stay idle when using a small `maxStartPerUserForTaskForQueue` setting. This setting
helps to maintain a good balance between bulk throughput and low latency for
interactive operations. This setting is recommended to help protect the server
against users (and automation systems) which may be running the repo tool with high
sync (-j) counts.

Example:

```
 [quota "*"]
   softMaxStartPerUserForQueue = 3 SSH-Interavtive-Users
```

This config make sures that as soon as a specific user has 3 tasks running, it ensures
that there is still at least one idle thread remaining after the task is started.

Publication Schedule
--------------------

Publication of repository sizes to registered UsageDataPublishedListeners
is configured in the `plugin.quota` subsection of the `gerrit.config` file.
The publication interval can be configured using the same format as for the
[garbage collection schedule](../../../Documentation/config-gerrit.html#gc),
with the parameter names 'publicationStartTime' and 'publicationInterval'.

Example:

```
  [plugin "quota"]
    publicationStartTime = Fri 10:30
    publicationInterval  = 1 day
```

If no publicationInterval is configured, no data is published.
