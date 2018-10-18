//License
/***
 * Java TelnetD library (embeddable telnet daemon)
 * Copyright (c) 2000-2005 Dieter Wimberger 
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * Neither the name of the author nor the names of its contributors
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *  
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDER AND CONTRIBUTORS ``AS
 * IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE 
 * POSSIBILITY OF SUCH DAMAGE.
 ***/

package net.wimpi.telnetd.shell;

import net.wimpi.telnetd.BootException;
import net.wimpi.telnetd.util.StringUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

/**
 * This class implements a Manager Singleton that takes care for all shells to
 * be offered.<br>
 * <p/>
 * The resources can be defined via properties that contain following
 * information:
 * <ul>
 * <li>All system defined shells:
 * <ol>
 * <li>Login: first shell run on top of the connection.
 * <li>Queue: shell thats run for connections placed into the queue.
 * <li>Admin: shell for administrative tasks around the embedded telnetd.
 * </ol>
 * <li>Custom defined shells:<br>
 * Declared as value to the <em>customshells</em> key, in form of a comma
 * seperated list of names. For each declared name there has to be an entry
 * defining the shell.
 * </ul>
 * The definition of any shell is simply represented by a fully qualified class
 * name, of a class that implements the shell interface. Please read the
 * documentation of this interface carefully.<br>
 * The properties are passed on creation through the factory method, which is
 * called by the net.wimpi.telnetd.TelnetD class.
 *
 * @author Dieter Wimberger
 * @version 2.0 (16/07/2006)
 * @see net.wimpi.telnetd.shell.Shell
 */
public class ShellManager {

	private static final Log					LOG	= LogFactory.getLog(ShellManager.class);
	private static ShellManager					SELF;										//Singleton reference
	private Map<String, Shell>					shells;
	private Map<String, Class<? extends Shell>>	shellClasses;

	private ShellManager() {
	}//constructor

	/**
	 * Private constructor, instance can only be created via the public factory
	 * method.
	 */
	private ShellManager(Map<String, String> shells) {
		SELF = this;
		this.shells = new HashMap<>(shells.size());
		this.shellClasses = new HashMap<>(shells.size());
		setupShells(shells);
	}//constructor

	/**
	 * Accessor method for shells that have been set up.<br>
	 * Note that it uses a factory method that any shell should provide via a
	 * specific class operation.<br>
	 *
	 * @param key
	 *            String that represents a shell name.
	 * @return Shell instance that has been obtained from the factory method.
	 */
	public Shell getShell(String key) {
		Shell sh = shells.get(key);
		if (sh != null)
			return sh;

		if (!shellClasses.containsKey(key))
			return null;

		Class<? extends Shell> cls = shellClasses.get(key);
		if (cls == null)
			return null;

		try {
			Method m = cls.getMethod("createShell");
			LOG.debug("[Factory Method] " + m.toString());
			return (Shell) m.invoke(cls);
		} catch (Exception e) {
			LOG.error("getShell()", e);
			return null;
		}
	}//getShell

	/**
	 * Method to initialize the system and custom shells whose names and classes
	 * are stored as keys within the shells.
	 * <p/>
	 * It allows other initialization routines to prepare shell specific
	 * resources. This is a similar procedure as used for Servlets.
	 */
	private void setupShells(Map<String, String> shells) {
		for (Entry<String, String> e : shells.entrySet()) {
			try {
				String name = e.getKey();
				String clsname = e.getValue();
				if (this.shells.containsKey(name)) {
					LOG.debug("Shell [" + name + "] already loaded");
					continue;
				}
				LOG.debug("Preparing Shell [" + name + "] " + clsname);
				@SuppressWarnings("unchecked")
				Class<? extends Shell> cls = (Class<? extends Shell>) Class.forName(clsname);
				shellClasses.put(name, cls);
			} catch (Exception ex) {
				LOG.error("setupShells", ex);
			}
		}
	}//setupShells

	/**
	 * Factory method for creating the Singleton instance of this class.<br>
	 * Note that this factory method is called by the net.wimpi.telnetd.TelnetD
	 * class.
	 *
	 * @param settings
	 *            Properties defining the shells as described in the class
	 *            documentation.
	 * @return ShellManager Singleton instance.
	 */
	public static ShellManager createShellManager(Properties settings) throws BootException {

		//Loading and applying settings
		try {
			LOG.debug("createShellManager()");
			Map<String, String> shells = new HashMap<>();
			//Custom shell definitions
			String sh = settings.getProperty("shells");
			if (sh != null) {
				for (String key : sh.split(",")) {
					String clsname = settings.getProperty("shell." + key + ".class");
					if (clsname == null) {
						LOG.debug("Shell entry named " + key + " not found.");
						throw new BootException("Shell " + key + " declared but not defined.");
					}
					shells.put(key, clsname);
				}
			}

			return new ShellManager(shells);

		} catch (Exception ex) {
			LOG.error("createManager()", ex);
			throw new BootException("Creating ShellManager Instance failed:\n" + ex.getMessage());
		}
	}//createManager

	/**
	 * creates an empty shell manager.
	 * 
	 * @param shells
	 *            a map of shell names to shell classes, eg.
	 *            <code>shell1=com.whatever.ShellOne, ...</code>
	 */
	public static ShellManager createShellManager(Map<String, String> shells) {
		return new ShellManager(shells);
	}

	/**
	 * Accessor method for the Singleton instance of this class.<br>
	 * Note that it returns null if the instance was not properly created
	 * beforehand.
	 *
	 * @return ShellManager Singleton instance reference.
	 */
	public static ShellManager getReference() {
		return SELF;
	}//getReference

	public Map<String, Shell> getShells() {
		return shells;
	}

}//class ShellManager