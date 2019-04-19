/*
 * IUserInterface.java
 *
 * Created on 9. Oktober 2003, 00:07
 *
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.ui.common;

import com.biglybt.core.Core;
import com.biglybt.core.CoreLifecycleListener;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

/**
 *
 * @author  Tobias Minich
 */
public interface IUserInterface {

  public void buildCommandLine(Options options);

  /** Initializes the UI.
   * The UI should not be started at this stage.
   *
   * @param first This UI Instance is the first on the command line and should take control of singular stuff (LocaleUtil and torrents added via Command Line).
   * @param others Indicates whether other UIs run along.
   */
  public void init(boolean first, boolean others);

  /**
   * Core has been created, but not fully initialize (No GlobalManager, etc)
   * <p/>
   * Add your {@link Core#addLifecycleListener(CoreLifecycleListener)} to
   * get your GlobalManager reference
   */
  public void coreCreated(Core core);

  /**
   * Process arguments coming either from command line, or from startserver
   * <p/>
   * may be called before core is started
   *
   * @param commands query-able list of command line options
   * @param args all the arguments
   * @return Unhandled arguments, or null you don't want any other UIs to be triggered
   */
  public String[] processArgs(CommandLine commands, String[] args);

  /**
   * Take control of the main thread, if you need to.  This is primarily for
   * UIs that want to start core themselves, or need their UI on the main thread.
   * <br>
   *   If you don't need to take control of the main thread, don't, so that
   *   another potential UI can (SWT)
   * <p/>
   * This method may never be triggered if an earlier UI took control of the
   * main thread.
   */
  public void takeMainThread();
}
