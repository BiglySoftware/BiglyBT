/*
 * Created on 12 May 2007
 * Created by Allan Crooks
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
 */
package com.biglybt.ui.swt.minibar;

import java.util.Iterator;
import java.util.ListIterator;
import java.util.ArrayList;

import com.biglybt.core.util.AEMonitor;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.common.updater.UIUpdater;
import com.biglybt.ui.swt.components.shell.ShellManager;

import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.common.updater.UIUpdatable;
import com.biglybt.ui.swt.uiupdater.UIUpdaterSWT;

/**
 * @author Allan Crooks
 *
 */
public class MiniBarManager
implements UIUpdatable
{

	private boolean global;

	private String type;

	private ArrayList minibars = new ArrayList();

	private static final AEMonitor minibars_mon = new AEMonitor("MiniBarManager");

	private final ShellManager shellManager = new ShellManager();

	private static MiniBarManager global_instance;
	static {
		global_instance = new MiniBarManager();
	}

	public static MiniBarManager getManager() {
		return global_instance;
	}

	// Intentionally package-private.
	MiniBarManager(String type) {
		this.global = false;
		this.type = type;
	}

	private MiniBarManager() {
		this.global = true;
		this.type = null;
	}

	public void register(MiniBar bar) {
		shellManager.addWindow(bar.getShell());
	    try {
	    	minibars_mon.enter();
	    	minibars.add(bar);
	    	if (!global) {global_instance.register(bar);}
	    } finally {
	    	minibars_mon.exit();
	    }
	  if (minibars.size() == 1) {
	  	try {
			  UIUpdater updater = UIUpdaterSWT.getInstance();
			  if (updater != null) {
				  updater.addUpdater(this);
			  }
			} catch (Exception e) {
				Debug.out(e);
			}
	  }
	}

	public void unregister(MiniBar bar) {
	    try {
	    	minibars_mon.enter();
	    	minibars.remove(bar);
	    	if (!global) {global_instance.unregister(bar);}
	    } finally {
	    	minibars_mon.exit();
	    }
	    if (minibars.isEmpty()) {
		  	try {
				  UIUpdater updater = UIUpdaterSWT.getInstance();
				  if (updater != null) {
					  updater.removeUpdater(this);
				  }
				} catch (Exception e) {
					Debug.out(e);
				}
	    }
	}

	public ShellManager getShellManager() {
		return shellManager;
	}

	public AEMonitor getMiniBarMonitor() {
		return minibars_mon;
	}

	public ListIterator getMiniBarIterator() {
		return this.minibars.listIterator();
	}

	public int countMiniBars() {
		return this.minibars.size();
	}


	public void setAllVisible(boolean visible) {
		try {
			minibars_mon.enter();

			for (Iterator iter = minibars.iterator(); iter.hasNext();) {
				MiniBar bar = (MiniBar) iter.next();
				bar.setVisible(visible);
			}
		}
		finally {
			minibars_mon.exit();
		}
	}

	  public void close(MiniBar mini_bar) {
		  	if (mini_bar != null) {
		  		mini_bar.close();
		  	}
		  }

	  public MiniBar getMiniBarForObject(Object context) {
			try {
				minibars_mon.enter();
				for (Iterator iter = minibars.iterator(); iter.hasNext();) {
					MiniBar bar = (MiniBar) iter.next();
					if (bar.hasContext(context)) {return bar;}
				}
				return null;
			}
			finally {
				minibars_mon.exit();
			}
	  }

		  public void close(Object context) {
			  MiniBar bar = this.getMiniBarForObject(context);
			  if (bar != null) {bar.close();}
			}

			public void closeAll() {
				try {
					minibars_mon.enter();
					for (Iterator iter = new ArrayList(minibars).iterator(); iter.hasNext();) {
						MiniBar bar = (MiniBar) iter.next();
						bar.close();
					}
				}
				finally {
					minibars_mon.exit();
				}
			}

			public boolean isOpen(Object context) {
				return this.getMiniBarForObject(context) != null;
			}

			// @see UIUpdatable#getUpdateUIName()
			@Override
			public String getUpdateUIName() {
				return "MiniBar-" + type;
			}

			// @see UIUpdatable#updateUI()
			@Override
			public void updateUI() {
				try {
					minibars_mon.enter();

					for (Iterator iter = minibars.iterator(); iter.hasNext();) {
						MiniBar bar = (MiniBar) iter.next();
						try {
							bar.refresh();
						} catch (Exception e) {
							Debug.out(e);
						}
					}
				} finally {
					minibars_mon.exit();
				}
			}
}
