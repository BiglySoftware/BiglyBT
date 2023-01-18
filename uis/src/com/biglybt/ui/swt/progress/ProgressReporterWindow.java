/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.ui.swt.progress;

import java.util.ArrayList;
import java.util.Arrays;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.AERunnable;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.shell.ShellFactory;
import com.biglybt.ui.swt.twistie.ITwistieListener;

import com.biglybt.ui.swt.UIFunctionsManagerSWT;
import com.biglybt.ui.swt.UIFunctionsSWT;

public class ProgressReporterWindow
	implements IProgressReportConstants, ITwistieListener, DisposeListener
{
	private Shell shell;

	private ScrolledComposite scrollable;

	private Composite scrollChild;

	private IProgressReporter[] pReporters;

	/**
	 * A registry to keep track of all reporters that are being displayed in all instances
	 * of this window.
	 * @see #isOpened(IProgressReporter)
	 */
	private static final ArrayList reportersRegistry = new ArrayList();

	/**
	 * A special boolean to track whether this window is opened and is showing the empty panel;
	 * mainly used to prevent opening more than one of these window when there are no reporters to work with
	 */
	private static boolean isShowingEmpty = false;

	/**
	 * The default width for the shell upon first opening
	 */
	private int defaultShellWidth = 500;

	/**
	 * The maximum number of panels to show when the window first open
	 */
	private int initialMaxNumberOfPanels = 3;

	/**
	 * The style bits to use for this panel
	 */
	private int style;

	/**
	 * Convenience variable tied to the parameter "auto_remove_inactive_items"
	 */
	private boolean isAutoRemove = false;

	/**
	 * Construct a <code>ProgressReporterWindow</code> for a single <code>ProgressReporter</code>
	 * @param pReporter
	 */
	private ProgressReporterWindow(IProgressReporter pReporter, int style) {
		this.style = style;
		if (null != pReporter) {
			pReporters = new IProgressReporter[] {
				pReporter
			};

		} else {
			pReporters = new IProgressReporter[0];
		}

		createControls();
	}

	/**
	 * Construct a single <code>ProgressReporterWindow</code> showing all <code>ProgressReporter</code>'s in the given array
	 * @param pReporters
	 */
	private ProgressReporterWindow(IProgressReporter[] pReporters, int style) {
		this.style = style;
		if (null != pReporters) {
			this.pReporters = pReporters;

		} else {
			this.pReporters = new IProgressReporter[0];
		}

		createControls();
	}

	/**
	 * Opens the window and display the given <code>IProgressReporter</code>
	 * <code>style</code> could be one or more of these:
	 * <ul>
	 * <li><code>IProgressReportConstants.NONE				</code>	-- the default</li>
	 * <li><code>IProgressReportConstants.AUTO_CLOSE	</code>	-- automatically disposes this panel when the given reporter is done</li>
	 * <li><code>IProgressReportConstants.MODAL				</code>	-- this window will be application modal</li>
	 * <li><code>IProgressReportConstants.SHOW_TOOLBAR</code> -- shows the toolbar for removing inactive reporters</li>
	 * </ul>
	 * @param pReporter
	 * @param style
	 */
	public static void open(IProgressReporter pReporter, int style) {
		new ProgressReporterWindow(pReporter, style).openWindow();
	}

	/**
	 * Opens the window and display the given array of <code>IProgressReporter</code>'s
	 * <code>style</code> could be one or more of these:
	 * <ul>
	 * <li><code>IProgressReportConstants.NONE				</code>	-- the default</li>
	 * <li><code>IProgressReportConstants.AUTO_CLOSE	</code>	-- automatically disposes this panel when the given reporter is done</li>
	 * <li><code>IProgressReportConstants.MODAL				</code>	-- this window will be application modal</li>
	 * <li><code>IProgressReportConstants.SHOW_TOOLBAR</code> -- shows the toolbar for removing inactive reporters</li>
	 * </ul>
	 * @param pReporters
	 * @param style
	 */
	public static void open(IProgressReporter[] pReporters, int style) {
		new ProgressReporterWindow(pReporters, style).openWindow();
	}

	/**
	 * Returns whether this window is already opened and is showing the empty panel
	 * @return
	 */
	public static boolean isShowingEmpty() {
		return isShowingEmpty;
	}

	/**
	 * Returns whether the given <code>IProgressReporter</code> is opened in any instance of this window;
	 * processes can query this method before opening another window to prevent opening multiple
	 * windows for the same reporter.  This is implemented explicitly instead of having the window automatically
	 * recycle instances because there are times when it is desirable to open a reporter in more than one
	 * instances of this window.
	 * @param pReporter
	 * @return
	 */
	public static boolean isOpened(IProgressReporter pReporter) {
		return reportersRegistry.contains(pReporter);
	}

	private void createControls() {
		/*
		 * Sets up the shell
		 */

		int shellStyle = SWT.DIALOG_TRIM | SWT.RESIZE;
		if ((style & MODAL) != 0) {
			shellStyle |= SWT.APPLICATION_MODAL;
		}

		shell = ShellFactory.createMainShell(shellStyle);
		shell.setText(MessageText.getString("progress.window.title"));
		
		Utils.setShellIcon(shell);

		GridLayout gLayout = new GridLayout();
		gLayout.marginHeight = 0;
		gLayout.marginWidth = 0;
		shell.setLayout(gLayout);

		/*
		 * Using ScrolledComposite with only vertical scroll
		 */
		scrollable = new ScrolledComposite(shell, SWT.V_SCROLL);
		scrollable.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		/*
		 * Main content composite where panels will be created
		 */
		scrollChild = new Composite(scrollable, SWT.NONE);

		GridLayout gLayoutChild = new GridLayout();
		gLayoutChild.marginHeight = 0;
		gLayoutChild.marginWidth = 0;
		gLayoutChild.verticalSpacing = 0;
		scrollChild.setLayout(gLayoutChild);
		scrollable.setContent(scrollChild);
		scrollable.setExpandVertical(true);
		scrollable.setExpandHorizontal(true);

		/*
		 * Re-adjust scrollbar setting when the window resizes
		 */
		scrollable.addControlListener(new ControlAdapter() {
			@Override
			public void controlResized(ControlEvent e) {
				Rectangle r = scrollable.getClientArea();
				scrollable.setMinSize(scrollChild.computeSize(r.width, SWT.DEFAULT));
			}
		});

		/*
		 * On closing remove all reporters that was handled by this instance of the window from the registry
		 */
		shell.addListener(SWT.Close, new Listener() {
			@Override
			public void handleEvent(Event event) {

				/*
				 * Remove this class as a listener to the disposal event for the panels or else
				 * as the shell is closing the panels would be disposed one-by-one and each one would
				 * force a re-layouting of the shell.
				 */
				Control[] controls = scrollChild.getChildren();
				for (int i = 0; i < controls.length; i++) {
					if (controls[i] instanceof ProgressReporterPanel) {
						((ProgressReporterPanel) controls[i]).removeDisposeListener(ProgressReporterWindow.this);
					}
				}

				/*
				 * Removes all the reporters that is still handled by this window
				 */
				for (int i = 0; i < pReporters.length; i++) {
					reportersRegistry.remove(pReporters[i]);
				}

				isShowingEmpty = false;
			}
		});

		if (pReporters.length == 0) {
			createEmptyPanel();
		} else {
			createPanels();
		}

		/*
		 * Shows the toolbar if specified
		 */
		if ((style & SHOW_TOOLBAR) != 0) {
			createToolbar();
		}
		isAutoRemove = COConfigurationManager.getBooleanParameter("auto_remove_inactive_items");

	}

	/**
	 * Creates a the toolbar at the bottom of the window
	 */
	private void createToolbar() {
		Composite toolbarPanel = new Composite(shell, SWT.NONE);
		toolbarPanel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false));
		GridLayout gLayout = new GridLayout(3, false);
		gLayout.marginWidth = 25;
		gLayout.marginTop = 0;
		gLayout.marginBottom = 0;
		toolbarPanel.setLayout(gLayout);

		final Button autoClearButton = new Button(toolbarPanel, SWT.CHECK);
		autoClearButton.setText(MessageText.getString("Progress.reporting.window.remove.auto"));
		Utils.setTT(autoClearButton,MessageText.getString("Progress.reporting.window.remove.auto.tooltip"));
		autoClearButton.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER,
				false, false));

		autoClearButton.setSelection(COConfigurationManager.getBooleanParameter("auto_remove_inactive_items"));

		Label dummy = new Label(toolbarPanel, SWT.NONE);
		dummy.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

		final Button clearInActiveButton = new Button(toolbarPanel, SWT.NONE);
		clearInActiveButton.setText(MessageText.getString("Progress.reporting.window.remove.now"));
		Utils.setTT(clearInActiveButton,MessageText.getString("Progress.reporting.window.remove.now.tooltip"));
		clearInActiveButton.setLayoutData(new GridData(SWT.END, SWT.CENTER, false,
				false));
		clearInActiveButton.setEnabled(!COConfigurationManager.getBooleanParameter("auto_remove_inactive_items"));

		/*
		 * Toggles the checked state of auto remove
		 */
		autoClearButton.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				COConfigurationManager.setParameter("auto_remove_inactive_items",
						autoClearButton.getSelection());

				/*
				 * Disable clearInActiveButton if auto remove is checked
				 */
				clearInActiveButton.setEnabled(!autoClearButton.getSelection());

				isAutoRemove = autoClearButton.getSelection();

				/*
				 * Removes any inactive panels that may already be in the window if this option is set to true
				 */
				if (isAutoRemove) {
					removeInActivePanels();
				}

			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
				widgetSelected(e);
			}

		});

		/*
		 * Remove inactive when clicked
		 */
		clearInActiveButton.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				removeInActivePanels();
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
				widgetSelected(e);
			}

		});
	}

	/**
	 * Removes all panels whose reporter is no longer active
	 */
	private void removeInActivePanels() {
		Control[] controls = scrollChild.getChildren();
		for (int i = 0; i < controls.length; i++) {
			if (null == controls[i] || controls[i].isDisposed()) {
				continue;
			}
			if (controls[i] instanceof ProgressReporterPanel) {
				IProgressReporter pReporter = ((ProgressReporterPanel) controls[i]).getProgressReporter();
				if (!pReporter.getProgressReport().isActive()) {

					if ( !pReporter.getProgressReport().isInErrorState()){

						ProgressReportingManager.getInstance().remove(pReporter);

						controls[i].dispose();
					}
				}
			}
		}
	}

	/**
	 * Creates just an empty panel with a message indicating there are no reports to display
	 */
	private void createEmptyPanel() {
		GridData gData = new GridData(SWT.FILL, SWT.FILL, true, true);
		gData.heightHint = 100;
		Composite emptyPanel = Utils.createSkinnedComposite(scrollChild, SWT.BORDER, gData);
		emptyPanel.setLayout(new GridLayout());
		Label nothingToDisplay = new Label(emptyPanel, SWT.NONE);
		nothingToDisplay.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		nothingToDisplay.setText(MessageText.getString("Progress.reporting.no.reports.to.display"));

		/*
		 * Mark this as being opened and is showing the empty panel
		 */
		isShowingEmpty = true;

	}

	/**
	 * Set initial size and layout for the window then open it
	 */
	private void openWindow() {

		/*
		 * Using initialMaxNumberOfPanels as a lower limit we exclude all other panels from the layout,
		 * compute the window size, then finally we include all panels back into the layout
		 *
		 *  This ensures that the window will fit exactly the desired number of panels
		 */
		Control[] controls = scrollChild.getChildren();
		for (int i = (initialMaxNumberOfPanels); i < controls.length; i++) {
			((GridData) controls[i].getLayoutData()).exclude = true;
		}

		Point defaultSize = shell.computeSize(defaultShellWidth, SWT.DEFAULT);

		for (int i = 0; i < controls.length; i++) {
			((GridData) controls[i].getLayoutData()).exclude = false;
		}
		formatLastPanel(null);
		scrollChild.layout();

		boolean alreadyPositioned = Utils.linkShellMetricsToConfig( shell, "com.biglybt.ui.swt.progress.ProgressReporterWindow" );

		/*
		 * Set the shell size if it's different that the computed size
		
		 * Hmm, why, if the user has resized it them so be it
		 * 
		if (!shell.getSize().equals(p)) {
			shell.setSize(p);
			shell.layout(false);
		}
		*/
		
		if ( !alreadyPositioned ){
			
			shell.setSize( defaultSize );
			
			shell.layout(false);
			
			/*
			 * Centers the window
			 */
	
			UIFunctionsSWT uiFunctions = UIFunctionsManagerSWT.getUIFunctionsSWT();
			if (null == uiFunctions) {
				/*
				 * Centers on the active monitor
				 */
				Utils.centreWindow(shell);
			} else {
				/*
				 * Centers on the main application window
				 */
				Utils.centerWindowRelativeTo(shell, uiFunctions.getMainShell());
			}
		}
		
		if ( COConfigurationManager.getBooleanParameter( "Reduce Auto Activate Window" )){
			
			shell.setVisible( true );
			
			shell.setFocus();
			
		}else{
		
				// shell.open does a bringToFront :(
			
			shell.open();
		}
	}

	public int
	getStyle()
	{
		return( style );
	}
	
	private void createPanels() {

		int size = pReporters.length;

		/*
		 * Add the style bit for standalone if there is zero or 1 reporters
		 */
		if (size < 2) {
			style |= STANDALONE;
		}

		for (int i = 0; i < size; i++) {
			if (null != pReporters[i]) {

				/*
				 * Add this reporter to the registry
				 */
				reportersRegistry.add(pReporters[i]);

				/*
				 * Create the reporter panel; adding the style bit for BORDER
				 */
				final ProgressReporterPanel panel = new ProgressReporterPanel( this,
						scrollChild, pReporters[i], style | BORDER);

				panel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

				panel.addTwistieListener(this);
				panel.addDisposeListener(this);
				pReporters[i].addListener(new AutoRemoveListener(panel));

			}
		}

		formatLastPanel(null);
	}

	/**
	 * Formats the last <code>ProgressReporterPanel</code> in the window to extend to the bottom of the window.
	 * This method will iterate from the last panel backward to the first, skipping over the given panel.
	 * @param panelToIgnore
	 */
	private void formatLastPanel(ProgressReporterPanel panelToIgnore) {
		Control[] controls = scrollChild.getChildren();

		for (int i = controls.length - 1; i >= 0; i--) {
			if (!controls[i].equals(panelToIgnore)) {
				((GridData) controls[i].getLayoutData()).grabExcessVerticalSpace = true;
				break;
			}
		}
	}

	/**
	 * Remove the given <code>IProgressReporter</code> from the <code>pReporters</code> array; resize the array if required
	 * @param reporter
	 */
	private void removeReporter(IProgressReporter reporter) {

		/*
		 * Removes it from the registry
		 */
		reportersRegistry.remove(reporter);

		/*
		 * The array is typically small so this is good enough for now
		 */

		int IDX = Arrays.binarySearch(pReporters, reporter);
		if (IDX >= 0) {
			IProgressReporter[] rps = new IProgressReporter[pReporters.length - 1];
			for (int i = 0; i < rps.length; i++) {
				rps[i] = pReporters[(i >= IDX ? i + 1 : i)];
			}
			pReporters = rps;
		}
	}

	/**
	 * When any <code>ProgressReporterPanel</code> in this window is expanded or collapsed
	 * re-layout the controls and window appropriately
	 */
	@Override
	public void isCollapsed(boolean value) {
		if (null != shell && !shell.isDisposed()) {
			scrollable.setRedraw(false);
			Rectangle r = scrollable.getClientArea();
			scrollable.setMinSize(scrollChild.computeSize(r.width, SWT.DEFAULT));

			/*
			 * Resizing to fit the panel if there is only one
			 */
			if (pReporters.length == 1) {
				Point p = shell.computeSize(defaultShellWidth, SWT.DEFAULT);
				if (shell.getSize().y != p.y) {
					p.x = shell.getSize().x;
					shell.setSize(p);
				}
			}

			scrollable.layout();
			scrollable.setRedraw(true);
		}
	}

	/**
	 * When any <code>ProgressReporterPanel</code> in this window is disposed
	 * re-layout the controls and window appropriately
	 */
	@Override
	public void widgetDisposed(DisposeEvent e) {

		if (e.widget instanceof ProgressReporterPanel) {
			ProgressReporterPanel panel = (ProgressReporterPanel) e.widget;
			removeReporter(panel.pReporter);

			panel.removeTwistieListener(this);

			/*
			 * Must let the GridLayout manager know that this control should be ignored
			 */
			((GridData) panel.getLayoutData()).exclude = true;
			panel.setVisible(false);

			/*
			 * If it's the last reporter then close the shell itself since it will be just empty
			 */
			if (pReporters.length == 0) {
				if ((style & AUTO_CLOSE) != 0) {
					if (null != shell && !shell.isDisposed()) {
						shell.close();
					}
				} else {
					createEmptyPanel();
				}
			} else {

				/*
				 * Formats the last panel; specifying this panel as the panelToIgnore
				 * because at this point in the code this panel has not been removed
				 * from the window yet
				 */
				formatLastPanel(panel);
			}

			if (null != shell && !shell.isDisposed()) {
				shell.layout(true, true);
			}
		}
	}

	/**
	 * Listener to reporters so we can remove the corresponding <code>ProgressReporterPanel</code> is the option
	 * <code>isAutoRemove</code> = <code>true</code>
	 * @author knguyen
	 *
	 */
	private class AutoRemoveListener
		implements IProgressReporterListener
	{
		private ProgressReporterPanel panel = null;

		private AutoRemoveListener(ProgressReporterPanel panel) {
			this.panel = panel;
		}

		@Override
		public int report(IProgressReport progressReport) {

			if (isAutoRemove && !progressReport.isActive() && !progressReport.isInErrorState()) {
				if (null != panel && !panel.isDisposed()) {
					ProgressReportingManager.getInstance().remove(
							panel.getProgressReporter());

					Utils.execSWTThread(new AERunnable() {
						@Override
						public void runSupport() {
							panel.dispose();
						}
					});
				}
				return RETVAL_OK_TO_DISPOSE;
			}
			return RETVAL_OK;
		}

	}
}
