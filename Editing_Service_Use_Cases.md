## Editing Services Use Cases

This is part of defining the API between the Editing Client (EC) and the Editing Service (ES). We work through some uses starting simple and then getting more elaborate. We assume the idea of an RDFPatch, RDFPatchLog (a sequence of RDFPatches) and json-ld serialization of resources.

### Edit one existing resource
In this case the user wants to make some changes to an existing resource. The EC:

- gets the resource ID of the resource to be edited from a search that the user then selects from or perhaps the user "knows" the ID and just types it in
- then makes a request to lds-pdi (or maybe the ES) for a json-ld serialization of the resource
- once the resource is received then the EC populates the editing UI, making ready for the user to review and edit the resource - edits may involve adding information, deleting or updating existing information
- upon the first edit action by the user, EC creates a new RDFPatch to record the user's changes.
- the user makes edits involving adding, deleting or updating existing information, and the EC records each action as a sequence of RDFPatch `A` and `D` patches. Each `A` or `D` consists of a quad of a `subject property object graph`