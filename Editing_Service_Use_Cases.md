## Editing Service Use Cases

This document is part of defining the editing API between the Editing Client (_EC_) and the Editing Service (_ES_). We work through some uses cases starting simple and then getting more elaborate. There are several other APIs that are applicable to interactions with the ES or other services including an:

- _Editing Profile API_ - a profile maintains editing user preferences and other information pertinent to supporting the user's use of EC, and 
- _Live Search API_ - this supports the editing user picking out a out a Person, Work, etc for inserting into a field in the EC UI. For example, the user wants to pick out the resource corresponding to the author of a Work. They know, at east a fragment of, the name and want to identify the intended resource from a small list of possibilities.

These APIs are not discussed further here.

These use cases here are discussed in terms of a relatively implementation neutral vocabulary that includes:

- _Task_ - is a collection of edits, by a _user_, to one or more _Resources_, some of which may already exist on the public Fuseki, others of which are to be created when the task is _run_. The edits may be accumulated over a period of several sessions. By default a task is associated with the user that created it. It is plausible that the task may be _handed off_ to another user, but in all cases there is at anytime a single user in control of the task. There is **no** concurrent sharing of the task across users.

- _Session_(s) - that represent a set of edits that a user makes in a task over a comparatively short time such as during a day at the office. The task can be considered as a sequence of sessions, with each session containing a sequence of edits to the group of resources. The user may request their EC to _save_ the task state (which includes the edits across one or more sessions). There is no discussion at this point of how sessions are represented within a task, but an idea is mentioned below. Every time a user saves the state of the task they are working on to ES this marks the end of a session. Resumption of the task marks the beginning of a new session within the task. Saving the task _may_ be no more than transmitting just the edits made in the current session or may involve sending the cumulative state of the task to ES (TBD). In any event the edits are organized as a patch.

- _Patch_ - is the representation of the edits that the user makes in a task. This representation/format is illustrated below. The patch format is designed to be simple to implement on both EC and ES. When the user resumes a task, EC will _interpret_ the patch to arrive at a state of the UI that represents the work of the user up to the end of the just previous session. For a new task there's no state and as the user requests resources to be created and/or retrieved for editing the UI gradually is filled out with the information using a display similar to the appearance of the resources under edit on the public library UI. The idea being that the editing user should be able to see their work displayed as it will appear when _published_ to the public site. This may entail a _preview_ feature if the UI needs for editing are too different from the public display.

With these bits of vocabulary in hand we work through some use cases.

### Edit one existing resource
In this case the user wants to make some changes to an existing resource:

- EC gets the resource ID of the resource to be edited from a search that the user then selects from or perhaps the user "knows" `theID` and just types it in or maybe the EC provides some bookmark feature (perhaps via the editing profile).

- EC then makes a `GET http://purl.bdrc.io/graph/theID` request for a json-ld serialization of the resource. 

- once the resource is received, the EC populates the editing UI, making ready for the user to review and edit the resource - edits may involve adding information, deleting or updating existing information

- **upon the first edit action by the user**, EC creates a new `Task` and `Session` to record the user's changes. 
  The `Task` needs a `ShortName` assigned by the user, a `TaskMessage` that describes the task, and a system-wide unique `TaskID` that is used to identify the task at ES. The `ShortName` is used to later identify the task from a list of tasks that the user may be working on, and the message provides a useful description of the task that may be visible to administrators, managers, as well as the user.
  
  The `Session` information can consist of the time the session is started, and perhaps the location of the user, and eventually the time the session was concluded.

  Further, EC will start accumulating edits by including an `H graph <http://purl.bdrc.io/graph/theID> .`(or some other encoding) in the patch text of the task. `graph` headers are used to inform EC what resources need to be retrieved and displayed in the UI, and the ES uses this information for internal bookkeeping.

  **Also at this point EC requests ES to lock resource**, `theID`. No need in Fuseki since ES is the only agent with write access to the Fuseki dataset. Via a lock, a single-writer/multiple-reader policy can be implemented, and this mitigates conflicts if EC:

  ```
    POST http://purl.bdrc.io/tasks?lock=theID
        header includes user id and authorization info
  ```
  with responses:

  - `202`(accepted)

  and

  - `409`(conflict)

  so that if a `409` is received then EC denies the user edit access - the UI display of `theID` remains in read/view mode. 
  
  In practice the library staff rarely runs into a situation where they want to edit a resource _checked out_ by another user. When it does happen it requires administrator access to release the lock. It usually happens when the user deletes a locked resource from their workspace without releasing the lock via a request to the app server. This is protected against but sometimes a workspace becomes corrupted or deleted and unless the user remembers all of the resources they have checked out then there are locked resources. We need appropriate administrative functionality to handle these cases.

- the user makes edits involving adding, deleting or updating existing information, and the EC records each action as a sequence of patch editing `A`(add) and `D`(delete) steps. Each `A` or `D` consists of a quad of `subject property object graph`. In this case the `graph` is that for the resource being edited, `http://purl.bdrc.io/graph/theID`.

- once the user is finished with their updates and signals to EC that the update is completed then EC performs a

  ```
    POST http://purl.bdrc.io/tasks/taskID?run
        header includes the task ShortName; user id and authorization info
        the task/session/patch payload
  ```
- ES receives the task and then ES saves the task data in a storage area local to ES, and then ES runs the task - which consists of updating the dataset on Fuseki and the appropriate local git repo and perhaps pushes to the public repo. Upon a successful apply, ES responds w/ 

    - `202`(accepted)
    
    The task `ShortName` and `TaskID` are returned along w/ other info tbd.

	On failure ES responds with one of:

    - `400`(bad request) - syntax error and so on, 
    - `403`(forbidden) - not authorized, 
    - `408`(request timeout) - timed out waiting for the complete request
    - `409`(conflict) - task is in conflict with the current state of the resource (an intervening update occurred perhaps). This error would also be returned in the event that the `taskID` has already been used (shouldn't occur)
    - `410`(gone) - a resource being updated by the task is no longer available
    - `500`(internal server error) - unexpected internal failure
    - `503`(service unavailable) - temporary overload, planned maintenance, Fuseki or git repos not available,e tc. 

    with appropriate error information.

    In any case, ES having been requested to _run_ the task releases any locks held on behalf of the user's task.

This completes this use case.

### Create one new resource
The user wants to add a new Work, Person, etc, with no references to any existing resources.

- The user requests EC to setup up the editing UI for creating a new resource of a specified type. EC must create a new resource ID, `theID` - the current idea is that EC will generate a UUID or other hash and prefix it with the usual `W`, `P`, etc depending on the type.

- upon the first edit action by the user, EC creates a new `Task` and `Session` as above. EC begins accumulating edits with a create header,  `H create <http://purl.bdrc.io/graph/theID> .`, instead of `H graph ... .`. This informs EC to just create an empty display ad populate with the user's edits versus retrieving a resource. ES uses this information for internal bookkeeping purposes. There is no lock to request in this case.

- the user makes edits involving adding, deleting or updating existing information, and the EC records each editing action in the task. Each edit uses the same graph term as above, `http://purl.bdrc.io/graph/theID`.

- once the user is finished with their updates and signals to EC that the update is completed then, as [above](#edit-one-existing-resource), EC performs:

  ```
    POST http://purl.bdrc.io/tasks/taskID?run
        header includes task ShortName and user id and authorization info
        the task/session/patch payload
  ```

- ES receives the task and saves it in a local store and runs the task after checking that the supplied resource ID, `theID`, is not in use with responses as above. ES will also add `adm:GitInfo` for the new resource.

### Create a resource referring to an existing resource

The user may want to create a new Work by an already existing Person and make a reference to the Person as a _Main Author_, `R0ER0019`. This use case proceeds as w/ [create one...](#create-one-new-resource) with the addition that EC must provide a method for the user to identify the Person resource that is to be referred to. This may be done w/ a _live search_ of Persons that allows the user to pick out the appropriate Person resource.

In this use case, there is no need for EC to get the Person resource since the user doesn't have any updates to make to the Person resource.

As a general comment, EC will need to mint a globally unique ID (aqs in creating `theID`) for the `:AgentAsCreator` resource that will be added to the graph begin constructed. There are many instances during editing where id minting will need to occur for creating named resources such as `:WorkTitle`, `:AgentAsCreator`, `:WorkEvent`, `:Note`, and others depending the type of the resource. The remainder of this use case is as described for previous use cases.

### Create a resource and view a referred to resource

This case proceeds as [just above](#create-a-resource-referring-to-an-existing-resource) except that here EC will be signalled by the user that the referred to resource needs to be retrieved and a new window or tab made available to allow the user to review the referred to resource; otherwise, much the same.

### Create a resource and update a referred to resource

This proceeds as just discussed except that the user signals EC that they are updating the referred to resource by taking a UI action that adds, deletes, or updates information about the referred to resource via the tab or window displaying the referred to resource (**request a lock at this point**).

In this instance EC will add `H graph <http://purl.bdrc.io/graph/referredID> .` to the task edit actions and the various edit actions w.r.t. the _referred to_ resource will include this graph id, like:

```
    A <http://purl.bdrc.io/resource/referredId> <http://purl.bdrc.io/ontology/core/seeAlso> <http://purl.bdrc.io/resource/theId> <http://purl.bdrc.io/graph/referredID> .
```
for this case the task will look schematically like:

```
    H id <taskId> .
    H shortName "addingNewFPL" .
    H message "adding a new FPL work just received" .
    H create <http://purl.bdrc.io/graph/theID> .
    H graph <http://purl.bdrc.io/graph/referredID> .
    S start 2019-05-21T08:49:19-05:00
    TX .
    A <subject1> <property1> <object1> <http://purl.bdrc.io/graph/theID> .
    . . .
    D <subjectN> <propertyN> <objectN> <http://purl.bdrc.io/graph/referredID> .
    A <subjectN> <propertyN> <objectN+1> <http://purl.bdrc.io/graph/referredID> .
    . . .
    TC .
    S end 2019-05-21T09:17:43-05:00
```
EC will submit the task as in previous cases:

```
    POST http://purl.bdrc.io/tasks/taskID?run
        header includes task ShortName and user id and authorization info
        the task payload
```

### Editing in several sessions

The user proceeds to create, refer, and update some set of resources as in previous cases but before they are ready to run the task, the user wants to take a break, maybe go home for the day and work from home or the next day back at their office and so on. For this case the user signals EC that they want to _save_ their work up to this point and EC performs:

```
    POST http://purl.bdrc.io/tasks/taskID?save
        header includes task ShortName and user id and authorization info
        the task payload
```
when ES receives the request the task is saved in a local storage area for future reference.

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
This task contains edits up to this point and EC will _play_ the task by 

- first retrieving any resources listed via `H graph <...> ` in the task and populating a UI tab or window with the contents of each; and creating empty UI editing areas for each `H create <...> `.

- then EC performs the _adds_ and _deletes_ in order in the task to set the UI up with the state when the user saved their work from the previous session.

- when the user starts editing in this state, EC opens a session and appends edits to the task:

  ```
    ... .
    S start 2019-05-22T10:08:17-05:00 .
    A ... .
    ...
  ```

- the user proceeds to perform editing operations and when finished either signals that they want to save the work up to this point, which EC implements via:

  ```
    PUT http://purl.bdrc.io/tasks/taskID?save
        header includes task and user info
        the task payload
  ```

  EC will include the `taskID` in the `PUT` headers in a form to be defined as the API is fleshed out. `PUT` is used to signal that `taskID` is an update to the task referenced in the http request.

  or the user signals to run the task:

  ```
    PUT http://purl.bdrc.io/tasks/taskID?run
        header includes the task and user info
        the task payload
  ```
  to which ES applies and responds as previously discussed.

  It is also possible for the user to request ES to _drop_ a previously saved task:

  ```
    PUT http://purl.bdrc.io/tasks/taskID?drop
        header includes the task and user info
        the task payload
  ```
  In this case, ES marks the `taskID` as `dropped` and releases any locks held by the task on behalf of the user. Dropping a task is requested when the user has checked out some resources (hence locking these resources) and after reviewing/editing (some of)them deciding to not make any changes, and then simply signals EC to request the task to be dropped.

#### how does the task oriented API work with adm:status?
It's worth considering the `adm:Status` values: `bda:StatusEditing`, `bda:StatusProvisional`, `bda:StatusReleased`. How might these fit in? A user may want to indicate that they are `bda:StatusEditing` several resources. 

Permissions may allow staff and such to see these resources in search results where public users will not. This will aid the editing users to be able to review the effects of edits visible in the public library context w/o making the resources visible to the public.

The `bda:StatusProvisional` may indicate that the editor of the resource has finished editing and is ready to have the resource(s) reviewed for release on the public library site. Then resources are changed to `bda:StatusReleased`.

There's work to be done (in any event) on the public library side with this; because now there is an opportunity to indicate to the library, lds-pdi, and queries, what resource statuses are permitted in the response to a library request. This could be via an HTTP header field that we define: `Accept_Status: bda:StatusEditing` (which can imply provisional and released - assuming these three status values to be ordered).

Library searches have to filter on status values that are accepted or not anyway. 

On the current, tbrc.org, site if a user edits a resource and sets the status to editing/provisional then they can view in tbrc.org by a show request. These resources are never returned in search results and if the user _checksout_ a resource and publishes it as editing/provisional then that resource is no longer visible in public library search results until the resource is published as released. In practice the librarians usually do **not** mark a released resource as editing/provisional. Rather, they just leave it marked released and publish to the master resource db with their changes and republish if they see the changes were not what they intended.

THus saving a task is useful in creating/editing resources according to the above use cases and the `adm:status` is relevant to managing resource visibility in various states. Also, the `Accept_Status: bda:StatusEditing` is an improvement over tbrc.org which provides no way for a library search to return editing/provisional results.

#### gitRevision chicken and egg:
Having `adm:gitRevision` in Fuseki in the `adm:GitInfo` referred to from the `adm:AdminData` which is in the graph in the git repo means that ES will be responsible for updating the local git resource repos to obtain the `adm:gitRevision`(s) and then adding those to the `adm:GitInfo` for each resource on-the-fly as the graphs are `put` on to Fuseki.

### Other user resumes

It may be necessary for another user to resume a previously saved task because the original user needs help or has become unavailable and so on. This will require some means via some combination of groups, roles, permissions and so on that allows another user to resume and review a task This could include the original user designating the next user or group etc. Or in case the original user is unavailable an administrator can reassign. How this case will be managed will require further definition and is not necessary in the initial release.

### Status and logs

We've already suggested that:

```
    GET http://purl.bdrc.io/tasks
        user id and authorization info
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
