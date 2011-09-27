/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.server;

import java.io.File;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import org.apache.log4j.Logger;
import org.rsna.util.FileUtil;
import org.rsna.util.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A class to extend the org.rsna.server.Users abstract class
 * for managing the users.xml file. This file is located in the root
 * of the application.
 */
public class UsersXmlFileImpl extends Users {

	static final Logger logger = Logger.getLogger(UsersXmlFileImpl.class);

	static String usersFileName = "users.xml";
	File usersFile = null;
	Hashtable<String,User> users = null;
	HashSet<String> roles = null;

	/**
	 * Constructor.
	 */
	public UsersXmlFileImpl() {
		//Load the roles table.
		roles = new HashSet<String>();

		//Load the users table from the XML file, creating
		//an empty XML file if it does not exist.
		usersFile = new File(usersFileName);
		if (!usersFile.exists()) FileUtil.setText(usersFile, getEmptyUsersText());
		users = getUsers();
	}

	/**
	 * Get all the User objects in a Hashtable indexed by username.
	 * @return the User objects or null if unable to get them.
	 */
	public synchronized Hashtable<String,User> getUsers() {
		Hashtable<String,User> hashtable = new Hashtable<String,User>();

		Document usersXML;
		try { usersXML = XmlUtil.getDocument(usersFile); }
		catch (Exception ex) {
			logger.warn("Unable to parse the users file: "+usersFile);
			return hashtable;
		}

		Element root = usersXML.getDocumentElement();
		Node userChild = root.getFirstChild();
		while (userChild != null) {
			if ((userChild instanceof Element) && userChild.getNodeName().equals("user")) {
				Element userElement = (Element)userChild;
				String username = userElement.getAttribute("username");
				String password = userElement.getAttribute("password");
				User user = new User(username,password);
				Node roleChild = userElement.getFirstChild();
				while (roleChild != null) {
					if ((roleChild instanceof Element) && roleChild.getNodeName().equals("role")) {
						user.addRole(roleChild.getTextContent());
					}
					roleChild = roleChild.getNextSibling();
				}
				hashtable.put(username,user);
			}
			userChild = userChild.getNextSibling();
		}
		return hashtable;
	}

	/**
	 * Get all the usernames in an alphabetized array.
	 * @return the array of usernames or a zero-length array if unable.
	 */
	public synchronized String[] getUsernames() {
		if (users == null) return new String[0];
		String[] usernames = new String[users.size()];
		usernames = users.keySet().toArray(usernames);
		Arrays.sort(usernames);
		return usernames;
	}

	/**
	 * Add a role name.
	 * @param role the role name.
	 */
	public synchronized void addRole(String role) {
		roles.add(role);
	}

	/**
	 * Get all the role names in a HashSet.
	 * @return the HashSet of role names or null if unable.
	 */
	public synchronized HashSet<String> getRoles() {
		if (users == null) return null;
		//Put in all the roles from the table,
		//just in case somebody has created new roles.
		for (User user : users.values()) {
			roles.addAll(user.getRoles());
		}
		return roles;
	}

	/**
	 * Get all the role names in an alphabetized array.
	 * @return the array of role names or a zero-length array if unable.
	 */
	public synchronized String[] getRoleNames() {
		HashSet<String> hashset = getRoles();
		if (hashset == null) return new String[0];
		String[] names = new String[hashset.size()];
		names = hashset.toArray(names);
		Arrays.sort(names);
		return names;
	}

	/**
	 * Reset the database of users.
	 * @param users the table of users to put in the database.
	 */
	public synchronized void resetUsers(Hashtable<String,User> users) {
		this.users = users;
		FileUtil.setText(usersFile, getUsersText());
	}

	/**
	 * Get a specific user.
	 * @param username the username
	 * @return the user or null if unable.
	 */
	public synchronized User getUser(String username) {
		return users.get(username);
	}

	/**
	 * Check whether a set of credentials match a user in the system.
	 * @return true if the credentials match a user; false otherwise.
	 */
	public boolean authenticate(String username, String password) {
		User user = getUser(username);
		return ((user != null) && user.getPassword().equals(password));
	}

	/**
	 * Add a user to the database or update the user if it exists.
	 * This method always updates the users.xml file.
	 * @param user the user to add or update.
	 */
	public synchronized void addUser(User user) {
		if (users == null) return;
		users.put(user.getUsername(), user);
		FileUtil.setText(usersFile, getUsersText());
	}

	/**
	 * Get the users in an XML Document, where the users are alphabetized by username.
	 */
	public Document getXML() {
		try {
			Document doc = XmlUtil.getDocument();
			Element root = doc.createElement("users");
			doc.appendChild(root);
			String[] names = getUsernames();
			for (String name : names) {
				User user = users.get(name);
				Element userElement = doc.createElement("user");
				root.appendChild(userElement);
				userElement.setAttribute("username",user.getUsername());
				userElement.setAttribute("password",user.getPassword());
				for (String role : user.getRoles()) {
					Element roleElement = doc.createElement("role");
					roleElement.setTextContent(role);
					userElement.appendChild(roleElement);
				}
			}
			return doc;
		}
		catch (Exception ex) { return null; }
	}

	private String getUsersText() {
		return XmlUtil.toString(getXML());
	}

	private String getEmptyUsersText() {
		return
			"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
			"<users>" +
				"<user username=\"king\" password=\"password\">" +
					"<role>admin</role>" +
					getUserRoles() +
					"<role>shutdown</role>" +
				"</user>" +
				"<user username=\"admin\" password=\"password\">" +
					"<role>admin</role>" +
					getUserRoles() +
				"</user>" +
			"</users>";
	}

	//Get the non-administrative roles
	private String getUserRoles() {
		StringBuffer sb = new StringBuffer();
		String[] roles = getRoleNames();
		for (String role : roles) {
			if (!role.equals("admin") && !role.equals("shutdown")) {
				sb.append("<role>"+role.trim()+"</role>");
			}
		}
		return sb.toString();
	}

}