/*
 * RHQ Management Platform
 * Copyright (C) 2005-2011 Red Hat, Inc.
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

package org.rhq.scripting.util;

import java.io.Reader;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.Collection;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;

/**
 * This is a decorator class for any other {@link ScriptEngine} implementation
 * that runs any of the eval methods with the defined set of {@link Permission}s. 
 * <p>
 * For the permissions to have any effect, a SecurityManager has to be installed 
 * in the current VM.
 * <p>
 * This class is provided in hopes that it can help provide security to script engines
 * that do not directly implement some kind of security measures.
 * 
 * @author Lukas Krejci
 */
public class SandboxedScriptEngine implements ScriptEngine {

    private ScriptEngine engine;
    private AccessControlContext accessControlContext;
        
    public SandboxedScriptEngine(ScriptEngine engine) {
        this.engine = engine;
    }
    
    public SandboxedScriptEngine(ScriptEngine engine, PermissionCollection permissions) {
        this(engine);
        setPermissions(permissions);
    }

    public SandboxedScriptEngine(ScriptEngine engine, Collection<? extends Permission> permissions) {
        this(engine);
        setPermissions(permissions);
    }

    public void setPermissions(Permission... permissions) {
        setPermissions(Arrays.asList(permissions));
    }
    
    public void setPermissions(Collection<? extends Permission> permissions) {
        Permissions ps = new Permissions();
        for(Permission p : permissions) {
            ps.add(p);
        }
        
        setPermissions(ps);
    }
    
    public void setPermissions(PermissionCollection permissions) {
        CodeSource cs = new CodeSource(null, (Certificate[]) null);
        
        ProtectionDomain domain = new ProtectionDomain(cs, permissions);        
        accessControlContext = new AccessControlContext(new ProtectionDomain[] { domain });
    }
    
    @Override
    public Object eval(final String script, final ScriptContext context) throws ScriptException {
        try {
            return AccessController.doPrivileged(new PrivilegedExceptionAction<Object>() {
                @Override
                public Object run() throws Exception {
                    return engine.eval(script, context);
                }
            }, accessControlContext);
        } catch (PrivilegedActionException e) {
            throw transfer(e);
        }        
    }

    @Override
    public Object eval(final Reader reader, final ScriptContext context) throws ScriptException {
        try {
            return AccessController.doPrivileged(new PrivilegedExceptionAction<Object>() {
                @Override
                public Object run() throws Exception {
                    return engine.eval(reader, context);
                }
            }, accessControlContext);
        } catch (PrivilegedActionException e) {
            throw transfer(e);
        }        
    }

    @Override
    public Object eval(final String script) throws ScriptException {
        try {
            return AccessController.doPrivileged(new PrivilegedExceptionAction<Object>() {
                @Override
                public Object run() throws Exception {
                    return engine.eval(script);
                }
            }, accessControlContext);
        } catch (PrivilegedActionException e) {
            throw transfer(e);
        }        
    }

    @Override
    public Object eval(final Reader reader) throws ScriptException {
        try {
            return AccessController.doPrivileged(new PrivilegedExceptionAction<Object>() {
                @Override
                public Object run() throws Exception {
                    return engine.eval(reader);
                }
            }, accessControlContext);
        } catch (PrivilegedActionException e) {
            throw transfer(e);
        }        
    }

    @Override
    public Object eval(final String script, final Bindings n) throws ScriptException {
        try {
            return AccessController.doPrivileged(new PrivilegedExceptionAction<Object>() {
                @Override
                public Object run() throws Exception {
                    return engine.eval(script, n);
                }
            }, accessControlContext);
        } catch (PrivilegedActionException e) {
            throw transfer(e);
        }        
    }

    @Override
    public Object eval(final Reader reader, final Bindings n) throws ScriptException {
        try {
            return AccessController.doPrivileged(new PrivilegedExceptionAction<Object>() {
                @Override
                public Object run() throws Exception {
                    return engine.eval(reader, n);
                }
            }, accessControlContext);
        } catch (PrivilegedActionException e) {
            throw transfer(e);
        }        
    }

    @Override
    public void put(String key, Object value) {
        engine.put(key, value);
    }

    @Override
    public Object get(String key) {
        return engine.get(key);
    }

    @Override
    public Bindings getBindings(int scope) {
        return engine.getBindings(scope);
    }

    @Override
    public void setBindings(Bindings bindings, int scope) {
        engine.setBindings(bindings, scope);
    }

    @Override
    public Bindings createBindings() {
        return engine.createBindings();
    }

    @Override
    public ScriptContext getContext() {
        return engine.getContext();
    }

    @Override
    public void setContext(ScriptContext context) {
        engine.setContext(context);
    }

    @Override
    public ScriptEngineFactory getFactory() {
        return engine.getFactory();
    }
    
    private static ScriptException transfer(PrivilegedActionException e) {
        if (e.getCause() instanceof ScriptException) {
            return (ScriptException) e.getCause();
        } else {
            return new ScriptException(e);
        }
    }
}
