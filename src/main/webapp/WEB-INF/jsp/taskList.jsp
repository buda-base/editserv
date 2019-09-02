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
<style>
.button {
  background-color: #4CAF50;
  border: none;
  color: white;
  padding: 10px 16px;
  text-align: center;
  text-decoration: none;
  display: inline-block;
  font-size: 12px;
  margin: 6px 3px;
  cursor: pointer;
}
h1 { color: #d04764; font-size: 38px; font-family: 'Signika', sans-serif; padding-bottom: 10px; }
h2 { color: #e6b035; font-size: 28px; font-family: 'Signika', sans-serif; padding-bottom: 10px; }
</style>
</head>
<body>
<h1>Tasks list</h1>
<c:if test = "${tasks.size()>0}">
    <h2>for user: ${tasks.get(0).getUser()}</h2>
</c:if>
    <div>
        <c:forEach items="${tasks}" var="task"> 
        <p> </p>
        <table id="specs" style="width: 70%" border="0">          
	        <tbody>
	           <tr>
	           <th><b>Task Id:</b> ${task.getId()}</th>
	           <th><input name="Edit" value="Edit" onClick="location.href='/taskEdit/${task.getId()}'" class="button" type="button">
	           <input name="Delete" value="Delete" onClick="location.href='/taskDelete/${task.getId()}'" class="button" type="button">
	           <input name="update" value="Submit" onClick="location.href='/taskSubmit/${task.getId()}'" class="button" type="button"></th>
	           </tr>
		        <tr>
		          <td style="width: 25%"><b>Message:</b></td>
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
        </c:forEach>
    </div>
</body>
</html>
