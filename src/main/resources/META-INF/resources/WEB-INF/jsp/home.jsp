<%@page contentType="text/html"%>
<%@page pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<!DOCTYPE html>
<html lang="en">
<head>    
    <title>Main page</title>
    <meta charset="UTF-8">
    <script src="https://code.jquery.com/jquery-1.10.2.js"></script>
<style>
form div {
    margin-bottom: 0.5em;
}
form div label, form div input {
    display: block;
    margin-bottom: 0.3em;
}
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
    padding-top: 12px;
    padding-bottom: 12px;
    text-align: left;
    background-color: #4e7F50;
    color: white;
}
</style>
</head>
<script type="text/javascript"> 
function showHide(el) { 
    var x = document.getElementById(el);    
    if (x.style.display === "none") {        
        x.style.display = "block";    
    } else {
        x.style.display = "none";    
    }
} 

</script>
<body>
    <h2 class="hello-title">Hello ${name}!</h2> 
    <br>
    <div><a href="javascript:showHide('newTask');">Create a new Task</a></div> 
    <br>
    <form id="create" name="create" action="" enctype='application/json'>
    <div id="newTask" style="display:block">
    <table id="specs" style="width: 100%;" border="0">
      <tr>
        <th colspan="2">Create a new Task</th>
      </tr>
      <tbody>
        <tr>
          <td>ID:<br>
          </td>
          <td><input name="id" size="50" value="${id}" type="text"><br>
          </td>
        </tr>
        <tr>
          <td>User:<br>
          </td>
          <td><input name="user" value="${user}" type="text"><br>
          </td>
        </tr>
        <tr>
          <td>Short name:<br>
          </td>
          <td><input name="shortName" type="text"><br>
          </td>
        </tr>
        <tr>
          <td>Message:<br>
          </td>
          <td><input name="message" type="text"><br>
          </td>
        </tr>
        <tr>
          <td>Patch:<br>
          </td>
          <td><textarea name="patch" cols="120" rows="20" wrap="soft"></textarea><br>
          </td>
        </tr>
        <tr>
          <td>Save message:</td>
          <td><input name="saveMsg" size="60" type="text"><br>
          </td>
        </tr>
      </tbody>
    </table>
    <br>
    <div><a href="javascript:showHide('newTask');">Hide form</a>&nbsp;<button>Create</button></div>
    <br> 
    </div>    
    </form>
    <div id="results"></div>
    <div>
    <table id="specs">      
            <tr>
                <th>TaskId</th>
                <th>Short name</th> 
                <th>Last save message</th>               
            </tr>           
            <c:forEach items="${tasks}" var="ts">
            <tr>
                <td><b>${ts.getId()}</b></td>
                <td>${ts.getShortName()}</td> 
                <td>${ts.getSaveMsg()}</td>               
            </tr>                          
            </c:forEach>
       </table>
    </div> 
    <br>
    ${tasks} 
    <div id="results"></div>   
</body>
</html>