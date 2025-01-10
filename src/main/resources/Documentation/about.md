This plugin allows to enforce quotas in Gerrit.

To protect a Gerrit installation it makes sense to limit the resources
that a project or group can consume. To do this a Gerrit administrator
can use this plugin to define quotas on project namespaces and define
rate limits per user group.

The @PLUGIN@ plugin supports the following quotas:

* Maximum number of projects in a namespace
* The maximum total file size of a repository in a namespace
* The maximum total file size of all repositories in a namespace

The measured repository sizes can be published periodically to registered
UsageDataPublishedListeners.

The @PLUGIN@ plugin supports the following rate limits:

* `uploadpack` requests which are executed when a client runs a fetch command.
* Maximum number of REST API calls

Rate limits define the maximum request rate for users in a given group
for a given request type.

**NOTE**:
* When rate limiting is enforced for REST API calls, it operates at
the HTTP protocol level. When a client exceeds the allowed
request rate, the server responds with an HTTP status code 429 (Too Many Requests).

* Unlike REST API rate limiting, rate limiting for Git upload pack operations
happens at the Git protocol level. Even if the rate limit is exceeded, the server
still responds with an HTTP status code 200 (OK), indicating a successful HTTP request.
However, within the Git protocol response, the client will receive a message indicating
that it has exceeded the rate limit.
