/*
 * Copyright (C) Bigly Software.  All Rights Reserved.
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

package com.biglybt.ui.swt.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.tag.TagFeatureRateLimit;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.pifimpl.local.ui.config.BooleanParameterImpl;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.config.actionperformer.IAdditionalActionPerformer;
import com.biglybt.ui.swt.mainwindow.ClipboardCopy;

/**
 * Checkbox SWT Parameter representing a Boolean value
 * <p/>
 * Will always use 2 horizontal spaces in GridLayout
 */
public class BooleanSwtParameter
	extends BaseSwtParameter<BooleanSwtParameter, Boolean>
{
	/**
	 * Value Processor that's parameterized for this class and Boolean values
	 */
	public interface ValueProcessor
		extends SwtParameterValueProcessor<BooleanSwtParameter, Boolean>
	{
		public default Boolean
		getValue(
			List<Boolean>	values )
		{
			int intB = -1;
			for ( boolean v: values ){
				if (intB == -1) {
					intB = v ? 1 : 0;
				} else if ((intB == 1) != v) {
					intB = 2;
					break;
				}
			}
			return( intB==2?null:intB == 1 );
		}
	}

	private final Label lblSuffix;

	private Label cbText;

	private IndentPaintListener indentPaintListener;

	final Button checkBox;

	final List<IAdditionalActionPerformer<Object>> performers = new ArrayList<>();

	public BooleanSwtParameter(Composite parent, BooleanParameterImpl paramInfo) {
		this(parent, paramInfo.getConfigKeyName(), paramInfo.getLabelKey(),
				paramInfo.getSuffixLabelKey(), null);
		setPluginParameter(paramInfo);
	}

	/**
	 * Make a checkbox.
	 * <p/>
	 * When parent is of GridLayout, resulting new widgets will take 2 columns
	 * 
	 * @param parent Where widgets will be placed. Parent is not altered
	 * @param paramID ID of the parameter (usually config id)
	 * @param labelKey Messagebundle key for the checkbox
	 * @param suffixLabelKey Messagebundle key for text after the checkbox (on a new row)
	 * @param valueProcessor null if you want to use COConfigurationManager
	 */
	public BooleanSwtParameter(Composite parent, String paramID, String labelKey,
		String suffixLabelKey,
			SwtParameterValueProcessor<BooleanSwtParameter, Boolean> valueProcessor) {
		super(paramID);

		boolean parentIsGridLayout = parent.getLayout() instanceof GridLayout;
		int parentColumns = parentIsGridLayout
				? ((GridLayout) parent.getLayout()).numColumns : 1;

		boolean toolong = false;
		String text = null;
		if (labelKey != null) {
			text = MessageText.getString(labelKey);
			toolong = text.indexOf('\n') > 0 || text.indexOf('\t') > 0
					|| text.length() > 100;
		}

		Composite ourParent;
		GridLayout ourParentGridLayout;
		if (suffixLabelKey == null && !toolong) {
			ourParent = parent;
		} else {
			ourParent = new Composite(parent, SWT.NONE);
			ourParentGridLayout = new GridLayout();
			ourParentGridLayout.marginHeight = ourParentGridLayout.marginWidth = ourParentGridLayout.horizontalSpacing = ourParentGridLayout.verticalSpacing = 0;
			ourParentGridLayout.numColumns = 2;
			ourParent.setLayout(ourParentGridLayout);

			if (parentIsGridLayout) {
				GridData gridData = new GridData(GridData.FILL_HORIZONTAL);
				gridData.horizontalSpan = parentColumns > 1 ? 2 : 1;
				ourParent.setLayoutData(gridData);
			}
		}

		if (text != null) {

			// Some plugins (Removal Rules) put \t at the beginning in hopes the control will be indented
			int numExtraIndent = 0;
			while (text.startsWith("\t")) {
				text = text.substring(1);
				numExtraIndent++;
			}

			if (toolong) {
				// Checkbox and text are two separate widgets
				checkBox = new Button(ourParent, SWT.CHECK);
				if (doGridData(ourParent)) {
					GridData gridData = new GridData();
					if (Constants.isUnix) {
						gridData.horizontalIndent = 4;
					}
					checkBox.setLayoutData(gridData);
				}else{
					checkBox.setLayoutData(new RowData());	// buttons need this on linux at least
				}
				
				cbText = new Label(ourParent, SWT.WRAP);
				Messages.setLanguageText(cbText, labelKey);
				
				if (doGridData(ourParent)) {
					GridData gridData = Utils.getWrappableLabelGridData(1, GridData.FILL_HORIZONTAL);
					gridData.horizontalIndent = Constants.isOSX ? -4 : 3;
					cbText.setLayoutData(gridData);
				}
				
				cbText.addListener(SWT.MouseDown, event -> {
					if (event.button == 1) {
						checkBox.forceFocus();
					}
				});
				cbText.addListener(SWT.MouseUp, event -> {
					if (event.button == 1) {
						setValue(!getValue());
					}
				});

				ourParent.addListener(SWT.Paint, event -> {
					if (checkBox.isDisposed() || event.gc == null) {
						return;
					}
					if (checkBox.isFocusControl()) {
						event.gc.setLineStyle(SWT.LINE_DOT);
						event.gc.setLineDash(new int[] {
							1,
							1
						});
						event.gc.setForeground(cbText.getForeground());
						Point size = cbText.getSize();
						Point location = cbText.getLocation();
						event.gc.drawRectangle(location.x - 1, 0, size.x + 1, size.y + 1);
					}
				});

				setRelatedControl(cbText);
				checkBox.addListener(SWT.FocusIn, event -> ourParent.redraw());
				checkBox.addListener(SWT.FocusOut, event -> ourParent.redraw());
			} else {
				// Checkbox with text in one widget
				checkBox = new Button(ourParent, SWT.CHECK);
				setRelatedControl(checkBox);
				if (numExtraIndent > 0) {
					checkBox.setText(text);
				} else {
					Messages.setLanguageText(checkBox, labelKey);
				}
				if (doGridData(ourParent)) {
					GridData gridData = new GridData();
					gridData.horizontalSpan = 2;
					checkBox.setLayoutData(gridData);
				}else{
					checkBox.setLayoutData(new RowData());	// buttons need this on linux at least
				}
			}

			if (numExtraIndent > 0) {
				setIndent(getIndent() + numExtraIndent, isIndentFancy());
			}
		} else {
			checkBox = new Button(ourParent, SWT.CHECK);
			setRelatedControl(checkBox);
			if (doGridData(ourParent)) {
				GridData gridData = new GridData();
				gridData.horizontalSpan = 2;
				checkBox.setLayoutData(gridData);
			}else{
				checkBox.setLayoutData(new RowData());	// buttons need this on linux at least
			}
		}
		setMainControl(checkBox);

		if (suffixLabelKey != null) {
			lblSuffix = new Label(ourParent, SWT.WRAP);

			Messages.setLanguageText(lblSuffix, suffixLabelKey);
			GridData gridData = Utils.getWrappableLabelGridData(2,
					GridData.FILL_HORIZONTAL);
			gridData.horizontalIndent = Constants.isOSX ? 45 : 40;
			lblSuffix.setLayoutData(gridData);
			ClipboardCopy.addCopyToClipMenu(lblSuffix);
			indentPaintListener = new IndentPaintListener(lblSuffix, 40);
		} else {
			lblSuffix = null;
		}

		if (valueProcessor != null) {
			setValueProcessor(valueProcessor);
		} else if (paramID != null) {
			setConfigValueProcessor(Boolean.class);
		}

		checkBox.addListener(SWT.Selection,
				event -> setValue(checkBox.getSelection()));
	}

	@Override
	public void setLayoutData(Object layoutData) {
		if (cbText == null) {
			super.setLayoutData(layoutData);
		} else {
			cbText.getParent().setLayoutData(layoutData);
		}
	}

	@Override
	public void setValueProcessor(
			SwtParameterValueProcessor<BooleanSwtParameter, Boolean> valueProcessor) {
		super.setValueProcessor(valueProcessor);
		triggerActionPerformers();
	}

	boolean performingActionsPerformers = false;

	public void triggerActionPerformers() {
		if (performers.size() == 0) {
			return;
		}
		if (!isEnabled()) {
			if (DEBUG) {
				debug("Skip  " + performers.size() + " actionperformers to "
						+ getValue() + " via " + Debug.getCompressedStackTrace());
			}
			return;
		}

		Boolean selected = getValue();
		if (DEBUG) {
			debug("Perform " + performers.size() + " actionperformers to " + selected
					+ " via " + Debug.getCompressedStackTrace());
		}
		if (selected == null) {
			return;
		}

		synchronized (performers) {
			if (performingActionsPerformers) {
				debug("Prevent cyclical performAction");
				return;
			}
			try {
				performingActionsPerformers = true;
				for (IAdditionalActionPerformer<Object> performer : performers) {
					if (DEBUG) {
						debug("Perform to " + selected + " for " + performer);
					}

					try {
						performer.valueChanged(selected);

						performer.performAction();
					} catch (Throwable t) {
						Debug.out(t);
					}
				}
			} finally {
				performingActionsPerformers = false;
			}
		}
	}

	public void setAdditionalActionPerformer(
			IAdditionalActionPerformer actionPerformer) {
		performers.add(actionPerformer);
		if (DEBUG) {
			debug(
					"add performer " + actionPerformer + ". count=" + performers.size());
		}
		Boolean selected = getValue();
		if (selected != null) {
			actionPerformer.valueChanged(selected);
		}
		if (isEnabled()) {
			actionPerformer.performAction();
		}
	}

	public List<IAdditionalActionPerformer<Object>> getAdditionalActionPerformers() {
		return performers;
	}

	@Override
	public Control[] getControls() {
		List<Control> list = new ArrayList<>(Arrays.asList(super.getControls()));
		if (cbText != null) {
			list.add(cbText);
		}
		if (lblSuffix != null) {
			list.add(lblSuffix);
			list.add(0, lblSuffix.getParent());
		}
		return list.toArray(new Control[0]);
	}

	/**
	 * Returns {@link #getValue()} as native boolean, converting null to false;
	 */
	public boolean isSelected() {
		Boolean value = getValue();
		return value == null ? false : value;
	}

	public void setSelected(final boolean _selected) {
		setValue(_selected);
	}

	@Override
	protected void triggerSubClassChangeListeners() {
		triggerActionPerformers();
	}

	@Override
	public void refreshControl() {
		super.refreshControl();
		Utils.execSWTThread(() -> {
			if (checkBox.isDisposed()) {
				return;
			}

			refreshSuffixControl(lblSuffix);

			Boolean selected = getValue();
			if (selected == null) {
				checkBox.setGrayed(true);
				checkBox.setSelection(true);
			} else {
				checkBox.setGrayed(false);
				if (checkBox.getSelection() != selected) {
					checkBox.setSelection(selected);
				}
			}
		});
	}

	@Override
	public void dispose() {
		performers.clear();
		if (indentPaintListener != null) {
			indentPaintListener.dispose();
			indentPaintListener = null;
		}
		super.dispose();
	}
}
