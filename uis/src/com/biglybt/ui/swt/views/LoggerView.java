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
package com.biglybt.ui.swt.views;

import java.io.PrintStream;
import java.text.FieldPosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.*;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.logging.*;
import com.biglybt.core.logging.impl.FileLogging;
import com.biglybt.core.util.AERunnable;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pifimpl.BasicPluginViewImpl;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListener;

/**
 * @author TuxPaper
 *
 * @since 2.3.0.5
 *
 * @note Plugin's Logging View is {@link BasicPluginViewImpl}
 */
public class LoggerView
	implements ILogEventListener, ParameterListener, UISWTViewCoreEventListener
{
	public static final String VIEW_ID = "LoggerView";

	//private final static LogIDs LOGID = LogIDs.GUI;

	private static final int COLOR_INFO = 0;

	private static final int COLOR_WARN = 1;

	private static final int COLOR_ERR = 2;

	private static Color[] colors = null;

	private static final int PREFERRED_LINES = 256;

	private static final int MAX_LINES = 1024 + PREFERRED_LINES;

	private static final SimpleDateFormat dateFormatter;

	private static final FieldPosition formatPos;

	public static final String MSGID_PREFIX = "ConsoleView";

	private Display display;

	private Composite panel;

	private StyledText consoleText = null;

	private Button buttonAutoScroll = null;

	private Object[] filter = null;

	// LinkedList is better for removing entries when full
	private LinkedList<LogEvent> buffer = new LinkedList<>();

	private boolean bPaused = false;

	private boolean bRealtime = false;

	private boolean bEnabled = false;

	private boolean bAutoScroll = true;

	private Pattern inclusionFilter;
	private Pattern exclusionFilter;

	// List of components we don't log.
	// Array represents LogTypes (info, warning, error)
	private ArrayList[] ignoredComponents = new ArrayList[3];

	private boolean stopOnNull = false;

	private UISWTView swtView;

	static {
		dateFormatter = new SimpleDateFormat("[HH:mm:ss.SSS] ");
		formatPos = new FieldPosition(0);
	}

	public LoggerView() {
		this(false);
		setEnabled(true);
	}

	public LoggerView(boolean stopOnNull) {
		for (int i = 0; i < ignoredComponents.length; i++) {
			ignoredComponents[i] = new ArrayList();
		}
		this.stopOnNull = stopOnNull;
	}

	public LoggerView(java.util.List<? extends LogEvent> initialList) {
		this();
		if (initialList != null)
			buffer.addAll(initialList);
		setEnabled(true);
	}

	private
	LoggerView(
		LoggerView		other )
	{
		buffer.addAll( other.buffer );

		for (int i = 0; i < ignoredComponents.length; i++) {
			ignoredComponents[i] = new ArrayList();
		}

		stopOnNull	= other.stopOnNull;

		setEnabled( other.bEnabled );
	}

	private void initialize(Composite composite) {
		display = composite.getDisplay();

		Colors.getInstance().addColorsChangedListener(this);
		parameterChanged("Color");

		panel = new Composite(composite, SWT.NULL);
		GridLayout layout = new GridLayout();
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		layout.verticalSpacing = 2;
		layout.numColumns = 2;
		panel.setLayout(layout);

		GridData gd;

		consoleText = new StyledText(panel, SWT.READ_ONLY | SWT.V_SCROLL
				| SWT.H_SCROLL);
		gd = new GridData(GridData.FILL_BOTH);
		gd.horizontalSpan = 2;
		consoleText.setLayoutData(gd);

		// XXX This doesn't work well, but it's better than nothing
		consoleText.addListener(SWT.Resize, new Listener() {
			@Override
			public void handleEvent(Event event) {
				GC gc = new GC(consoleText);
				int charWidth = gc.getFontMetrics().getAverageCharWidth();
				gc.dispose();

				int areaWidth = consoleText.getBounds().width;
				consoleText.setTabs(areaWidth / 6 / charWidth);
			}
		});

		consoleText.addListener(SWT.KeyDown, new Listener() {
			@Override
			public void handleEvent(Event event) {
				int key = event.character;
				if (key <= 26 && key > 0) {
					key += 'a' - 1;
				}
				if ((event.stateMask & SWT.MOD1) > 0  && key == 'a') {
					((StyledText) event.widget).selectAll();
				}
			}
		});

		ScrollBar sb = consoleText.getVerticalBar();
		sb.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				bAutoScroll = false;
				if (buttonAutoScroll != null && !buttonAutoScroll.isDisposed())
					buttonAutoScroll.setSelection(false);
			}
		});

		Composite cLeft = new Composite(panel, SWT.NULL);
		layout = new GridLayout();
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		layout.verticalSpacing = 1;
		cLeft.setLayout(layout);
		gd = new GridData(SWT.TOP, SWT.LEAD, false, false);
		cLeft.setLayoutData(gd);

		Button buttonPause = new Button(cLeft, SWT.CHECK);
		Messages.setLanguageText(buttonPause, "LoggerView.pause");
		gd = new GridData();
		buttonPause.setLayoutData(gd);
		buttonPause.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (e.widget == null || !(e.widget instanceof Button))
					return;
				Button btn = (Button) e.widget;
				bPaused = btn.getSelection();
				if (!bPaused && buffer != null) {
					refresh();
				}
			}
		});

		Button buttonRealtime = new Button(cLeft, SWT.CHECK);
		Messages.setLanguageText(buttonRealtime, "LoggerView.realtime");
		gd = new GridData();
		buttonRealtime.setLayoutData(gd);
		buttonRealtime.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (e.widget == null || !(e.widget instanceof Button))
					return;
				Button btn = (Button) e.widget;
				bRealtime = btn.getSelection();
			}
		});

		buttonAutoScroll = new Button(cLeft, SWT.CHECK);
		Messages.setLanguageText(buttonAutoScroll, "LoggerView.autoscroll");
		gd = new GridData();
		buttonAutoScroll.setLayoutData(gd);
		buttonAutoScroll.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (e.widget == null || !(e.widget instanceof Button))
					return;
				Button btn = (Button) e.widget;
				bAutoScroll = btn.getSelection();
			}
		});
		buttonAutoScroll.setSelection(true);

		Button buttonClear = new Button(cLeft, SWT.PUSH);
		Messages.setLanguageText(buttonClear, "LoggerView.clear");
		gd = new GridData();
		buttonClear.setLayoutData(gd);
		buttonClear.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				consoleText.setText("");
			}
		});

		/** FileLogging filter, consisting of a List of types (info, warning, error)
		 * and a checkbox Table of component IDs.
		 */
		final String sFilterPrefix = "ConfigView.section.logging.filter";
		Group gLogIDs = Utils.createSkinnedGroup(panel, SWT.NULL);
		Messages.setLanguageText(gLogIDs, "LoggerView.filter");
		layout = new GridLayout();
		layout.marginHeight = 0;
		layout.numColumns = 2;
		gLogIDs.setLayout(layout);
		gd = new GridData();
		gLogIDs.setLayoutData(gd);

		Label label = new Label(gLogIDs, SWT.NONE);
		Messages.setLanguageText(label, "ConfigView.section.logging.level");
		label.setLayoutData(new GridData());

		final Label labelCatFilter = new Label(gLogIDs, SWT.NONE);
		labelCatFilter.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL));

		final List listLogTypes = new List(gLogIDs, SWT.BORDER | SWT.SINGLE
				| SWT.V_SCROLL);
		gd = new GridData(SWT.NULL, SWT.BEGINNING, false, false);
		listLogTypes.setLayoutData(gd);

		final int[] logTypes = { LogEvent.LT_INFORMATION, LogEvent.LT_WARNING,
				LogEvent.LT_ERROR };
		for (int i = 0; i < logTypes.length; i++)
			listLogTypes.add(MessageText.getString("ConfigView.section.logging.log"
					+ i + "type"));
		listLogTypes.select(0);

		final LogIDs[] logIDs = FileLogging.configurableLOGIDs;
		//Arrays.sort(logIDs);

		Composite cChecksAndButtons = new Composite(gLogIDs, SWT.NULL);
		layout = new GridLayout(2, false);
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		cChecksAndButtons.setLayout(layout);
		cChecksAndButtons.setLayoutData(new GridData());

		final Composite cChecks = new Composite(cChecksAndButtons, SWT.NULL);
		RowLayout rowLayout = new RowLayout(SWT.VERTICAL);
		rowLayout.wrap = true;
		rowLayout.marginLeft = 0;
		rowLayout.marginRight = 0;
		rowLayout.marginTop = 0;
		rowLayout.marginBottom = 0;
		cChecks.setLayout(rowLayout);

		SelectionAdapter buttonClickListener = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				int index = listLogTypes.getSelectionIndex();
				if (index < 0 || index >= logTypes.length)
					return;
				Button item = (Button) e.widget;
				if (item.getSelection())
					ignoredComponents[index].remove(item.getData("LOGID"));
				else
					ignoredComponents[index].add(item.getData("LOGID"));
			}
		};
		for (int i = 0; i < logIDs.length; i++) {
			Button btn = new Button(cChecks, SWT.CHECK);
			btn.setText(MessageText.getString(sFilterPrefix + "." + logIDs[i],
					logIDs[i].toString()));

			btn.setData("LOGID", logIDs[i]);

			btn.addSelectionListener(buttonClickListener);

			if (i == 0) {
				gd = new GridData(SWT.FILL, SWT.FILL, false, false, 1, 2);
				gd.heightHint = (btn.computeSize(SWT.DEFAULT, SWT.DEFAULT).y + 2) * 3;
				cChecks.setLayoutData(gd);
			}
		}

		// Update table when list selection changes
		listLogTypes.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				int index = listLogTypes.getSelectionIndex();
				if (index < 0 || index >= logTypes.length)
					return;

				labelCatFilter.setText(MessageText.getString(
						"ConfigView.section.logging.showLogsFor", listLogTypes
								.getSelection()));

				Control[] items = cChecks.getChildren();
				for (int i = 0; i < items.length; i++) {
					if (items[i] instanceof Button) {
						LogIDs ID = (LogIDs) items[i].getData("LOGID");
						if (ID != null) {
							boolean checked = !ignoredComponents[index].contains(ID);
							((Button) items[i]).setSelection(checked);
						}
					}
				}
			}
		});

		listLogTypes.notifyListeners(SWT.Selection, null);

		Button btn;
		btn = new Button(cChecksAndButtons, SWT.PUSH);
		gd = new GridData();
		btn.setLayoutData(gd);
		Messages.setLanguageText(btn, "LoggerView.filter.checkAll");
		btn.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				int index = listLogTypes.getSelectionIndex();

				Control[] items = cChecks.getChildren();
				for (int i = 0; i < items.length; i++) {
					if (items[i] instanceof Button) {
						LogIDs ID = (LogIDs) items[i].getData("LOGID");
						if (ID != null && ignoredComponents[index].contains(ID)) {
							((Button) items[i]).setSelection(true);
							ignoredComponents[index].remove(ID);
						}
					}
				}
			}
		});

		btn = new Button(cChecksAndButtons, SWT.PUSH);
		gd = new GridData();
		btn.setLayoutData(gd);
		Messages.setLanguageText(btn, "LoggerView.filter.uncheckAll");
		btn.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				int index = listLogTypes.getSelectionIndex();

				Control[] items = cChecks.getChildren();
				for (int i = 0; i < items.length; i++) {
					if (items[i] instanceof Button) {
						LogIDs ID = (LogIDs) items[i].getData("LOGID");
						if (ID != null && !ignoredComponents[index].contains(ID)) {
							((Button) items[i]).setSelection(false);
							ignoredComponents[index].add(ID);
						}
					}
				}
			}
		});

		Composite cBottom = new Composite(panel, SWT.NONE);
		gd = new GridData(SWT.FILL, SWT.FILL, true, false);
		gd.horizontalSpan = 2;
		cBottom.setLayoutData(gd);
		cBottom.setLayout(new GridLayout(2, false));


		label = new Label(cBottom, SWT.NONE);
		label.setLayoutData(new GridData());
		Messages.setLanguageText(label, "LoggerView.includeOnly");

		final Text inclText = new Text(cBottom, SWT.BORDER);
		gd = new GridData();
		gd.widthHint = 200;
		inclText.setLayoutData(gd);
		inclText.addModifyListener(new ModifyListener()
		{
			@Override
			public void modifyText(ModifyEvent e) {
				String newExpression = inclText.getText();
				if (newExpression.length() == 0)
					inclusionFilter = null;
				else
				{
					try
					{
						inclusionFilter = Pattern.compile(newExpression, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE );
						inclText.setBackground(null);
					} catch (PatternSyntaxException e1)
					{
						inclText.setBackground(Colors.colorErrorBG);
					}
				}
			}
		});

		label = new Label(cBottom, SWT.NONE);
		label.setLayoutData(new GridData());
		Messages.setLanguageText(label, "LoggerView.excludeAll");

		final Text exclText = new Text(cBottom, SWT.BORDER);
		gd = new GridData();
		gd.widthHint = 200;
		exclText.setLayoutData(gd);
		exclText.addModifyListener(new ModifyListener()
		{
			@Override
			public void modifyText(ModifyEvent e) {
				String newExpression = exclText.getText();
				if (newExpression.length() == 0)
					exclusionFilter = null;
				else
				{
					try
					{
						exclusionFilter = Pattern.compile(newExpression, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE );
						exclText.setBackground(null);
					} catch (PatternSyntaxException e1)
					{
						exclText.setBackground(Colors.colorErrorBG);
					}
				}
			}
		});


		if (!Logger.isEnabled()) {
			consoleText.setText(MessageText.getString("LoggerView.loggingDisabled")
					+ "\n");
		}
	}

	private Composite getComposite() {
		return panel;
	}

	private void refresh() {
		if (bPaused)
			return;

		synchronized (buffer) {
			if (consoleText == null || consoleText.isDisposed())
				return;

			for (int i = 0; i < buffer.size(); i++) {
				try {
					LogEvent event = buffer.get(i);

					int nbLinesBefore = consoleText.getLineCount();
					if (nbLinesBefore > MAX_LINES)
					{
						consoleText.replaceTextRange(0, consoleText.getOffsetAtLine(PREFERRED_LINES), "");
						nbLinesBefore = consoleText.getLineCount();
					}


					final StringBuffer buf = new StringBuffer();
					buf.append('\n');

					dateFormatter.format(event.timeStamp, buf, formatPos);
					buf.append("{").append(event.logID).append("} ");

					buf.append(event.text);
					if (event.relatedTo != null) {
						buf.append("; \t| ");
						for (int j = 0; j < event.relatedTo.length; j++) {
							Object obj = event.relatedTo[j];
							if (j > 0)
								buf.append("; ");
							if (obj instanceof LogRelation) {
								buf.append(((LogRelation) obj).getRelationText());
							} else if (obj != null) {
								buf.append(obj.getClass().getName()).append(": '").append(
										obj.toString()).append("'");
							}
						}
					}


					String toAppend = buf.toString();

					if((inclusionFilter != null && !inclusionFilter.matcher(toAppend).find()) || (exclusionFilter != null && exclusionFilter.matcher(toAppend).find()))
						continue;

					int start = consoleText.getText().length();
					
					consoleText.append(toAppend);

					int nbLinesNow = consoleText.getLineCount();
					int colorIdx = -1;
					if (event.entryType == LogEvent.LT_INFORMATION)
						colorIdx = COLOR_INFO;
					else if (event.entryType == LogEvent.LT_WARNING)
						colorIdx = COLOR_WARN;
					else if (event.entryType == LogEvent.LT_ERROR)
						colorIdx = COLOR_ERR;

					if (colors != null && colorIdx >= 0){
						consoleText.setLineBackground(nbLinesBefore, nbLinesNow
								- nbLinesBefore, colors[colorIdx]);
						
						if ( Utils.isDarkAppearanceNative()){
							
							boolean useBlack = Colors.isBlackTextReadable( colors[colorIdx] );
							
							if ( useBlack ){
								StyleRange styleRange = new StyleRange();
								styleRange.start = start;
								styleRange.length = toAppend.length();
								styleRange.foreground = Colors.black;
								consoleText.setStyleRange(styleRange);
							}
						}
					}
				} catch (Exception e) {
					// don't send it to log, we might be feeding ourselves
					PrintStream ps = Logger.getOldStdErr();
					if (ps != null) {
						ps.println("Error writing event to console:");
						e.printStackTrace(ps);
					}
				}

			}
			buffer.clear();
			if (bAutoScroll)
				consoleText.setTopIndex(consoleText.getLineCount());
		}
	}

	private void delete() {
		Logger.removeListener(this);
		if (panel != null && !panel.isDisposed())
			panel.dispose();
		Colors instance = Colors.getInstance();
		if (instance != null) {
			instance.removeColorsChangedListener(this);
		}
	}

	private String getFullTitle() {
		return MessageText.getString("ConsoleView.title.full");
	}

	// @see com.biglybt.core.logging.ILogEventListener#log(com.biglybt.core.logging.LogEvent)
	@Override
	public synchronized void log(final LogEvent event) {
		if (display == null || display.isDisposed())
			return;

		if (ignoredComponents[logTypeToIndex(event.entryType)].contains(event.logID))
			return;

		// Always display STDERR messages, as they may relate to the filter
		boolean bMatch = (event.logID == LogIDs.STDERR || filter == null);

		if (!bMatch && event.relatedTo != null) {
			for (int i = 0; !bMatch && i < event.relatedTo.length; i++) {
				Object obj = event.relatedTo[i];

				if (obj == null)
					continue;

				for (int j = 0; !bMatch && j < filter.length; j++) {
					if (obj instanceof LogRelation) {
						//System.err.println(obj.getClass().getSimpleName() + " is Logrelation");

						Object newObj = ((LogRelation) obj).queryForClass(filter[j]
								.getClass());
						if (newObj != null)
							obj = newObj;
					}

					//System.err.println(obj.getClass().getName() + " matches " + filter[j].getClass().getSimpleName() + "?");

					if (obj == filter[j])
						bMatch = true;
				} // for filter
			} // for relatedTo
		}

		if (bMatch) {
			synchronized (buffer) {
				if (buffer.size() >= 200)
					buffer.removeFirst();
				buffer.add(event);
			}

			if (bRealtime && !bPaused) {
				Utils.execSWTThread(new AERunnable() {
					@Override
					public void runSupport() {
						refresh();
					}
				});
			}
		}
	}

	public void setFilter(Object[] _filter) {
		synchronized( this ){
			filter = _filter;
		}

		clearConsole();
	}

	private void clearConsole() {
		if (display == null || display.isDisposed())
			return;

		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (consoleText != null && !consoleText.isDisposed()) {
					consoleText.setText("");
				}
			}
		});
	}

	public void setEnabled(boolean on) {
		if (bEnabled == on)
			return;
		bEnabled = on;
		if (on)
			Logger.addListener(this);
		else
			Logger.removeListener(this);
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.PluginView#getPluginViewName()
	 */
	public String getPluginViewName() {
		return "Console";
	}

	// TODO: Support multiple selection
	private void dataSourceChanged(Object newDataSource) {
		if (newDataSource == null) {
			if (stopOnNull) {
				setEnabled(false);
				return;
			}
			setFilter(null);
		} else if (newDataSource instanceof Object[]) {
			setFilter((Object[]) newDataSource);
		} else if (newDataSource instanceof Boolean) {
			stopOnNull = ((Boolean) newDataSource);
			return;
		} else {
			setFilter(new Object[] { newDataSource });
		}

		setEnabled(true);
	}

	private int logTypeToIndex(int entryType) {
		switch (entryType) {
			case LogEvent.LT_INFORMATION:
				return 0;
			case LogEvent.LT_WARNING:
				return 1;
			case LogEvent.LT_ERROR:
				return 2;
		}
		return 0;
	}

	/*
	private int indexToLogType(int index) {
		switch (index) {
			case 0:
				return LogEvent.LT_INFORMATION;
			case 1:
				return LogEvent.LT_WARNING;
			case 2:
				return LogEvent.LT_ERROR;
		}
		return LogEvent.LT_INFORMATION;
	}
	*/

	@Override
	public void parameterChanged(String parameterName) {
		if (parameterName.startsWith("Color")) {
			Utils.execSWTThread(new AERunnable() {
				@Override
				public void runSupport() {
					if (display == null || display.isDisposed())
						return;

					if (colors == null)
						colors = new Color[3];

					final Color[] newColors = { Colors.blues[Colors.BLUES_MIDLIGHT],
							Colors.colorWarning, Colors.red_ConsoleView };
					boolean bColorChanged = false;

					for (int i = 0; i < newColors.length; i++) {
						if (colors[i] == null || colors[i].isDisposed()) {
							colors[i] = newColors[i];
							bColorChanged = true;
						}
					}

					if (bColorChanged && consoleText != null) {
						// remove color
						String text = consoleText.getText();
						consoleText.setText(text);
					}
				}
			});
		}
	}

	@Override
	public boolean eventOccurred(UISWTViewEvent event) {
    switch (event.getType()) {
      case UISWTViewEvent.TYPE_CREATE:
      	event.getView().setDestroyOnDeactivate(false);
      	swtView = (UISWTView)event.getData();
      	swtView.setTitle(getFullTitle());
        break;

      case UISWTViewEvent.TYPE_DESTROY:
        delete();
        break;

      case UISWTViewEvent.TYPE_INITIALIZE:
        initialize((Composite)event.getData());
        break;

      case UISWTViewEvent.TYPE_LANGUAGEUPDATE:
      	Messages.updateLanguageForControl(getComposite());
      	swtView.setTitle(getFullTitle());
        break;

      case UISWTViewEvent.TYPE_DATASOURCE_CHANGED:
      	dataSourceChanged(event.getData());
        break;

      case UISWTViewEvent.TYPE_FOCUSGAINED:
      	break;

      case UISWTViewEvent.TYPE_REFRESH:
        refresh();
        break;
    }

    return true;
  }
}
