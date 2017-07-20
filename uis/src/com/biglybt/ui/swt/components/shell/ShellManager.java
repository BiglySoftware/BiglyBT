package com.biglybt.ui.swt.components.shell;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.ui.swt.Utils;

import java.util.*;

/*
 * Created on 17-Mar-2005
 * Created by James Yeh
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 */

/**
 * ShellManager provides a logical grouping for a set of shells
 * <p><b>Note</b>: This class must be used from the SWT display thread</p>
 * @version 1.0
 * @author James Yeh
 * @see org.eclipse.jface.window.WindowManager
 */
public class ShellManager
{
    private static ShellManager instance;

    private final Collection<Shell> shells = new ArrayList<>();
    private final List addHandlers = new LinkedList();
    private final List removeHandlers = new LinkedList();

    static
    {
        instance = new ShellManager();
    }

    /**
     * <p>Gets the application's shared shell manager</p>
     * <p>This ShellManager has no bearing on other ShellManager instances</p>
     * <p><b>Note</b>: This method must be invoked by the SWT display thread</p>
     * @return
     */
    public static final ShellManager sharedManager()
    {
        return instance;
    }

    /**
     * Adds a shell to the shell manager. If the shell is already managed, it is not added again.
     * <p><b>Note</b>: This method must be invoked by the SWT display thread</p>
     * @param shell A SWT Shell
     */
    public final void addWindow(final Shell shell)
    {
        //Debug.out("Invoked by thread " + Thread.currentThread().getName());
        if(shells.contains(shell)) {return;}

        shells.add(shell);
        notifyAddListeners(shell);
        shell.addDisposeListener(new DisposeListener()
        {
            @Override
            public void widgetDisposed(DisposeEvent event)
            {
            	try {
                removeWindow(shell);
            	} catch (Exception e) {
            		Logger.log(new LogEvent(LogIDs.GUI, "removeWindow", e));
            	}
            }
        });
        shell.addListener(SWT.Show, new Listener() {
        	@Override
	        public void handleEvent(Event event) {
        		Utils.verifyShellRect(shell, false);
        	}
        });
    }

    /**
     * Removes a shell from the shell manager
     * <p><b>Note</b>: This method must be invoked by the SWT display thread</p>
     * @param shell A SWT Shell
     */
    public final void removeWindow(Shell shell)
    {
        shells.remove(shell);
        notifyRemoveListeners(shell);
    }

    /**
     * <p>Gets the shells managed by the manager as an Iterator</p>
     * <p>The order in which the shells were added are retained.</p>
     * <p><b>Note</b>: This method must be invoked by the SWT display thread</p>
     * @return The iterator
     */
    public final Iterator<Shell> getWindows()
    {
        return shells.iterator();
    }

    /**
     * Gets whether the ShellManager manages no shells
     * @return True if ShellManager is empty
     */
    public final boolean isEmpty()
    {
        return shells.isEmpty();
    }

    /**
     * Gets the number of shells the ShellManager manages
     * @return The number
     */
    public final int getSize()
    {
        return shells.size();
    }

    /**
     * <p>Invokes the handleEvent method specified by the SWT listener for each managed shell</p>
     * <p>The event's widget is set to the reference of the shell invoking it</p>
     * @param command A command implemented as a SWT Listener
     */
    public final void performForShells(final Listener command)
    {
        Iterator iter = shells.iterator();
        for(int i = 0; i < shells.size(); i++)
        {
            Shell aShell = (Shell)iter.next();
            Event evt = new Event();
            evt.widget = aShell;
            evt.data = this;
            command.handleEvent(evt);
        }
    }

    /**
     * Gets the set of managed shells
     * @return The set
     */
    protected final Collection getManagedShellSet()
    {
        return shells;
    }

    // events

    /**
     * <p>Adds a listener that will be invoked when a shell has been added to the ShellManager</p>
     * <p>The listener and the shell will automatically be removed when the shell is disposed</p>
     * @param listener A SWT Listener
     */
    public final void addWindowAddedListener(Listener listener)
    {
        addHandlers.add(listener);
    }

    /**
     * Removes a listener that will be invoked when a shell has been added to the ShellManager
     * @param listener A SWT Listener
     */
    public final void removeWindowAddedListener(Listener listener)
    {
        addHandlers.remove(listener);
    }

    /**
     * Adds a listener that will be invoked when a shell has been removed from the ShellManager
     * @param listener A SWT Listener
     */
    public final void addWindowRemovedListener(Listener listener)
    {
        removeHandlers.add(listener);
    }

    /**
     * Removes a listener that will be invoked when a shell has been removed from the ShellManager
     * @param listener A SWT Listener
     */
    public final void removeWindowRemovedListener(Listener listener)
    {
        removeHandlers.remove(listener);
    }

    /**
     * Notifies the WindowAddedListener handlers
     * @param sender A SWT shell that "sends" the events
     */
    protected final void notifyAddListeners(Shell sender)
    {
        Iterator iter = addHandlers.iterator();
        for(int i = 0; i < addHandlers.size(); i++)
        {
            ((Listener)iter.next()).handleEvent(getSWTEvent(sender));
        }
    }

    /**
     * Notifies the WindowRemovedListener handlers
     * @param sender A SWT shell that "sends" the events
     */
    protected final void notifyRemoveListeners(Shell sender)
    {
        Iterator iter = removeHandlers.iterator();
        for(int i = 0; i < removeHandlers.size(); i++)
        {
            ((Listener)iter.next()).handleEvent(getSWTEvent(sender));
        }
    }

    /**
     * <p>Gets a generated SWT Event based on the shell</p>
     * <p>The widget field of the event should be set to the shell</p>
     * @param shell A SWT Shell
     * @return The event
     */
    protected Event getSWTEvent(Shell shell)
    {
        Event e = new Event();
        e.widget = shell;
        e.item = shell;
        return e;
    }
}
