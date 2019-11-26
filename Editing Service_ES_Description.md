This file contains a description of the basic use case of the editing server in the server perspective.

## Editing Service Use Cases

This document is part of defining the editing API between the Editing Client (_EC_) and the Editing Service (_ES_). We work through some uses cases starting simple and then getting more elaborate. These are discussed here in terms of a relatively implementation neutral vocabulary that includes:

- _Task_ - is a collection of edits, by a _user_, to one or more _Resources_, some of which may already exist on the public Fuseki, others of which are to be created when the task is _run_. The edits may be accumulated over a period of several sessions. By default a task is associated with the user that created it. It is plausible that the task may be _handed off_ to another user, but in all cases there is at anytime a single user in control of the task. There is **no** concurrent sharing of the task across users.

- _Session_(s) - that represent a set of edits that a user makes in a task over a comparatively short time such as during a day at the office. The task can be considered as a sequence of sessions, with each session containing a sequence of edits to the group of resources. The user may request their EC to _save_ the task state (which includes the edits across one or more sessions). There is no discussion at this point of how sessions are represented within a task, but an idea is mentioned below. Every time a user saves the state of the task they are working on to ES this marks the end of a session. Resumption of the task marks the beginning of a new session within the task. Saving the task _may_ be no more than transmitting just the edits made in the current session or may involve sending the cumulative state of the task to ES (TBD). In any event the edits are organized as a patch.

- _Patch_ - is the representation of the edits that the user makes in a task. This representation/format is illustrated below. The patch format is designed to be simple to implement on both EC and ES. When the user resumes a task, EC will _interpret_ the patch to arrive at a state of the UI that represents the work of the user up to the end of the just previous session. A patch is created each time a new task is started. There is one and only one patch associated with a task.

With these bits of vocabulary in hand we work through some use cases.

### Edit or create a resource

In both cases, EC creates a new `Task` to record the user's changes :

- **upon the first edit action by the user**,  
  The `Task` needs a `ShortName` assigned by the user, a `TaskMessage` that describes the task, and a system-wide unique `TaskID` that is used to identify the task at ES. The `ShortName` is used to later identify the task from a list of tasks that the user may be working on, and the message provides a useful description of the task that may be visible to administrators, managers, as well as the user.
  
  The `Session` information can consist of the time the session is concluded, and its corresponding git version.

  Further, EC will start accumulating edits by including an `H graph <http://purl.bdrc.io/graph/theID> .`(or some other encoding) in the patch text of the task. `graph` headers are used to inform EC what resources need to be retrieved and displayed in the UI, and the ES uses this information for internal processing.

**Resource lock mechanism**

There is no resource lock mechanism, each editing session closure being a new git version of the working task. Conflicts can only occur at transaction time, ie when the user tells ES to actually apply the changes, in which case an administrator will have to resolve a git conflict.

*Note that locking each edited resource for an unknown period of time is not really practical, in particular when these resources are derived from affected resources in a bulk update (replace for instance).*
  
  In practice the library staff rarely runs into a situation where they want to edit a resource _checked out_ by another user but we need to plan for appropriate administrative functionality to handle these cases.

```
    POST http://purl.bdrc.io/tasks
        header includes authorization info. 
        and the task/session/patch payload        
```
- ES receives the task and queues the task in memory until a transaction for this task is started (see transactions and endpoint actions details in [here](https://github.com/buda-base/editserv/blob/master/editservDoc.odt)). 

This completes this use case.

### Editing in several sessions

The user proceeds to create, refer, and update some set of resources as in previous cases but before they are ready to run the task, the user wants to take a break, maybe go home for the day and work from home or the next day back at their office and so on. For this case the user signals EC that they want to _save_ their work up to this point and EC performs:

```
    PUT http://purl.bdrc.io/tasks
        header includes authorization info
        and the task payload
```
when ES receives the request the task is saved in a local giot repo for future reference.

Later the user wants to resume their task represented so far by the saved task. In order to resume the task the user will need to be able to signal EC as to which task to resume. Generally, this will require EC to request a list of saved tasks associated with the user - they may have several tasks in progress:

```
    GET http://purl.bdrc.io/tasks
        user id and authorization info
```

Not mentioned until now is that the requests from EC to ES contain authorization and user ID information in the requests. This permits ES to validate user permissions and so on and allows to group tasks by user ID.

Once the user is provided w/ a list of tasks or via some bookmark mechanism, etc then EC will request the task, `taskID`, from ES:

```
    GET http://purl.bdrc.io/tasks/taskID
        user id and authorization info
```
This task contains all the edits already made up to this point, organized as sessions. By default, EC will _play_ the latest session of this task.

- the user proceeds to perform editing operations and when finished either signals that they want to save the work up to this point, which EC implements via:

  ```
    PUT http://purl.bdrc.io/tasks   
        header includes authorization info
        and the task payload
  ```

  `PUT` is used to signal that this is an update to the task referenced in the http request payload.

  or the user signals to run the task:

  ```
    POST http://purl.bdrc.io/tasks
        header includes authorization info
        the task payload
  ```
  to which ES applies and responds as previously discussed.

  It is also possible for the user to request ES to _drop_ a previously saved task:

  ```
    DELETE http://purl.bdrc.io/tasks
        header iauthorization info
        and the task payload
  ```
  In this case, ES simply removes the task from the local git repo.   
  
#### gitRevision chicken and egg:
Having `adm:gitRevision` in Fuseki in the `adm:GitInfo` referred to from the `adm:AdminData` which is in the graph in the git repo means that ES will be responsible for updating the local git resource repos to obtain the `adm:gitRevision`(s) and then adding those to the `adm:GitInfo` for each resource on-the-fly as the graphs are `put` on to Fuseki.

### Other user resumes

It may be necessary for another user to resume a previously saved task because the original user needs help or has become unavailable and so on. This will require some means via some combination of groups, roles, permissions and so on that allows another user to resume and review a task This could include the original user designating the next user or group etc. Or in case the original user is unavailable an administrator can reassign. How this case will be managed will require further definition and is not necessary in the initial release.

### Status and logs

We've already suggested that:

```
    GET http://purl.bdrc.io/tasks
        authorization info
```

will lead to ES responding with a list of _in progress_ tasks for the user.

There are several other similar queries that we need to consider.

- request a list of tasks that the user has in progress, completed, and aborted. A user may want to review their work over a given time period for the purpose of making a status report for example

- request a list of tasks against a given resource - similar to a git history for a single item in a git repo

- request a list of tasks against a set of resources, possibly defined via a sparql query

This is not necessarily an exhaustive list of such requests. It's not clear what endpoint should be used here but it appears that `tasks` would be getting a bit overloaded with use cases or more query parameters or ...

### A thought on storing tasks on ES
One idea that we've discussed for storing tasks on ES is to have a local git repo for tasks. So when a task is submitted to ES for saving or running, ES will write the task contents to a text file and add as needed and commit to the task repo under the a name like: `userID/ShortName_taskID` or some such that will be useful for retrieving within the ES. The use of a git repo for tasks solves providing a log view of a task by requesting a git history for a `taskID`. This history will have an entry for each session == save or run made for the task. Getting the git history for a `userID` would give a log of the various tasks/sessions for a user.

The local git repo for ES may be periodically zipped and stored on S3 for backup. This should provide a sufficiently stable way to manage the tasks on behalf of the users.