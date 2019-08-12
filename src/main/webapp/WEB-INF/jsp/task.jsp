<!DOCTYPE html>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<html lang="en">
<head>
<style>
#specs {
    font-family: "Trebuchet MS", Arial, Helvetica, sans-serif;
    border-collapse: collapse;
    width: 95%;
}

#specs td, #customers th {
    border: 1px solid #ddd;
    padding: 8px;
}

#specs tr:nth-child(even){background-color: #f2f2f2;}

#specs tr:hover {background-color: #ddd;}

#specs th {
    padding-top: 11px;
    padding-bottom: 11px;
    text-align: left;
    background-color: #4e7F50;
    color: white;
}
</style>
<script type="text/javascript"> 
function showHide(id) {    
    var x = document.getElementById(id);    
    if (x.style.display === "none") {        
        x.style.display = "block";    
    } else {
        x.style.display = "none";    
    }
}   
</script>
</head>
<body>
    <div>
    <p><b>Task Id:</b> ${task.getId()}</p>
    <p><b>Current version:</b></p>
    <p><table id="specs" style="width: 80%" border="0">
      <tbody>
        <tr>
          <td><b>Message:</b></td>
          <td> ${task.getMessage()}</td>
        </tr>
        <tr>
          <td><b>Short name:</b></td>
          <td>${task.getShortName()}</td>
        </tr>
        <tr>
          <td><b>Patch:</b></td>
          <td><textarea rows=5 cols="100" readonly>${task.getPatch()}</textarea></td>
        </tr>
        <tr>
          <td><b>User:</b></td>
          <td>${task.getUser()}</td>
        </tr>
        <tr>
          <td><b>Save message:</b></td>
          <td>${task.getSaveMsg()}</td>
        </tr>
      </tbody>
    </table>
    <p><b>All sessions:</b></p>
    <table id="specs" style="width: 80%" border="0">
      <tbody>
        <tr>
          <th>Date</th>
          <th>Save Message</th>
          <th>Version</th>
          <th></th>
        </tr>
        <c:forEach items="${sessions}" var="sess"> 
        <tr>
          <td>${sess.getDate()} </td>
          <td>${sess.getTask().getSaveMsg()} </td>
          <td>${sess.getShortGitVersion()} </td>
          <td><a href="javascript:showHide('${sess.getShortGitVersion()}')">View details</a></td>
        </tr>
        <tr id="${sess.getShortGitVersion()}" style="display:none">
          <td colspan="4">
          <table id="specs" border="0">          
		      <tbody>
		        <tr>
		          <td><b>Message:</b></td>
		          <td> ${sess.getTask().getMessage()}</td>
		        </tr>
		        <tr>
		          <td><b>Short name:</b></td>
		          <td>${sess.getTask().getShortName()}</td>
		        </tr>
		        <tr>
		          <td><b>Patch:</b></td>
		          <td><textarea rows=5 cols="100" readonly>${sess.getTask().getPatch()}</textarea></td>
		        </tr>
		        <tr>
		          <td><b>User:</b></td>
		          <td>${sess.getTask().getUser()}</td>
		        </tr>
		        <tr>
		          <td><b>Save message:</b></td>
		          <td>${sess.getTask().getSaveMsg()}</td>
		        </tr>
	      </tbody>
          </table>
          </td>          
        </tr>
        </c:forEach>
      </tbody>
    </table>       
    </div>
</body>
</html>
