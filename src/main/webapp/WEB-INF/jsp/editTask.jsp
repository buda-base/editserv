<!DOCTYPE html>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@page import="io.bdrc.edit.testClient.*"%>
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
<script type="text/javascript"> 
function showHide(id) {    
    var x = document.getElementById(id);    
    if (x.style.display === "none") {        
        x.style.display = "block";    
    } else {
        x.style.display = "none";    
    }
}  
function submitForm(name)
{
	document.getElementById("put").value = name;		
	document.myform.submit();
}

</script>
</head>
<body>
<form id="myform" name="myform" action="" method="GET">
<h1>Task edition</h1>
    <div>
    <p><b>Task Id:</b> <input type="text" name="tskid" value="${task.getId()}" readonly></p>
    <p><b>Current version:</b></p>
    <p><table style="width: 80%" border="0">
      <tbody>
        <tr>
          <td style="width: 10%;text-align:right"><b>Message:</b></td>
          <td style="text-align:left"><input type="text" name="msg" value="${task.getMessage()}"></td>
        </tr>
        <tr>
          <td style="width: 10%;text-align:right"><b>Short name:</b></td>
          <td style="text-align:left"><input type="text" name="shortName" value="${task.getShortName()}"></td>
        </tr>        
        <tr>
          <td style="width: 10%;text-align:right"><b>User:</b></td>
          <td style="text-align:left">${task.getUser()}</td>
        </tr>
        <tr>
          <td style="width: 10%;text-align:right"><b>Save message:</b></td>
          <td style="text-align:left"><input type="text" name="saveMsg" placeholder="version message" value="${task.getSaveMsg()}"></td>
        </tr>
      </tbody>
    </table>
     <hr>
    <p><table style="width: 80%" border=0>
    <tbody>
        <tr>
          <td><b>Patch:</b></td>
          <td><textarea name="patch" rows=5 cols="100" readonly>${task.getPatch()}</textarea></td>
        </tr>
        </tbody>
    </table>
    <div id="quad" style="display:block">
    <span><b>Add a quad:</b></span>
    <table style="width: 40%" border=0>
    <tbody>
        <tr>
          <td style="text-align:right;width: 10%"><b>Command:</b></td>
          <td style="text-align:left"><select name="command" id="command">
		    <option value="A">Add</option>
		    <option value="D">Delete</option>		    
		</select>
          </td>
          <td></td>
          <td></td>
        </tr>
        <tr>
          <td style="text-align:right;width: 6%"><b>Subject:</b></td>
          <td style="text-align:left;width: 10%"><input type="text" name="subj" placeholder="subject"></td>
          <td style="text-align:right;width: 8%"><b>Subject type:</b></td>
          <td style="text-align:left;width: 10%">
	          <select name="type" id="type">
	            <option value="place">Place</option>
                <option value="topic">Topic</option>
	            <option value="work">Work</option>
	            <option value="person">Person</option>           
	          </select>
          </td>
        </tr>
        <tr>
          <td style="text-align:right;width: 10%"><b>Predicate:</b></td>
          <td style="text-align:left">
          <select name="predicate" id="predicate">
              <optgroup label="PLACE">
              <c:forEach items="${ResourceProps.getProps(ResourceProps.PLACE)}" var="pl_props">
			    <option value="${pl_props}">${pl_props}</option>
			  </c:forEach>			    
			  </optgroup> 
			  <optgroup label="TOPIC">
                <c:forEach items="${ResourceProps.getProps(ResourceProps.TOPIC)}" var="tp_props">
                <option value="${tp_props}">${tp_props}</option>
              </c:forEach>  
              </optgroup> 
              <optgroup label="WORK">
                <c:forEach items="${ResourceProps.getProps(ResourceProps.WORK)}" var="w_props">
                <option value="${w_props}">${w_props}</option>
              </c:forEach>  
              </optgroup> 
              <optgroup label="PERSON">
                <c:forEach items="${ResourceProps.getProps(ResourceProps.PERSON)}" var="p_props">
                <option value="${p_props}">${p_props}</option>
              </c:forEach>  
              </optgroup>          
          </select>
          </td>
          <td></td>
          <td></td>
        </tr>
        <tr>
          <td style="text-align:right;width: 10%"><b>Object:</b></td>
          <td style="text-align:left"><input type="text" name="obj" placeholder="object"></td>
          <td><input type="checkbox" id="literal" name="literal">
                <label for="horns">Is literal</label></td>
          <td></td>
        </tr>
        <tr>
          <td style="text-align:right;width: 10%"><b>Graph:</b></td>
          <td style="text-align:left"><input type="text" name="graph" placeholder="graph"></td>
          <td><input type="checkbox" id="create" name="create">
                <label for="horns">to create</label></td>
          <td></td>
        </tr>
        </tbody>
    </table>
    </div>
    <br>
    <input name="add" value="Add quad" class="button" type="submit">&nbsp;<input name="Edit" onClick="javascript:submitForm('save')" value="Save task" class="button" type="button">
    <hr>
    <c:if test = "${sessions.size()>0}">
    <p><b>History (all sessions):</b></p>
    <table id="specs" style="width: 80%" border=0>
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
          <table id="specs" border=0>          
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
    </c:if>      
    </div>
    <input type="hidden" name="put" id="put" value="">
    </form>
</body>
</html>
