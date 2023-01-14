/*
 * Created on 22-May-2004
 * Created by Paul Gardner
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

package com.biglybt.ui.swt.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.AEMonitor;
import com.biglybt.core.util.Debug;
import com.biglybt.pifimpl.local.ui.config.ParameterImpl;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.ClipboardCopy;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.views.ConfigView;
import com.biglybt.pif.ui.config.Parameter;
import com.biglybt.pif.ui.config.ParameterValidator.ValidationInfo;
import com.biglybt.pif.ui.config.ParameterWithSuffix;

public abstract class BaseSwtParameter<PARAMTYPE extends SwtParameter<VALUETYPE>, VALUETYPE>
	implements SwtParameter<VALUETYPE>
{
	protected final String paramID;

	private Control relatedControl;

	protected SwtParameterValueProcessor<PARAMTYPE, VALUETYPE> valueProcessor;

	protected List<ParameterChangeListener<PARAMTYPE>> change_listeners;

	private final List<SwtParameterValidator<PARAMTYPE, VALUETYPE>> validators = new ArrayList<>();

	private static final AEMonitor class_mon = new AEMonitor("Parameter:class");

	@SuppressWarnings("unchecked")
	private final PARAMTYPE thisTyped = (PARAMTYPE) this;

	private String oldTooltip = null;

	protected Parameter pluginParam;

	private IndentPaintListener fancyPaintListener = null;

	private ValidationInfo lastValidationInfo;

	private boolean enabled = true;

	private int indent;

	private Control mainControl;

	public BaseSwtParameter(String paramID) {
		this.paramID = paramID;
	}

	@Override
	public String getParamID() {
		return paramID;
	}

	public void setMainControl(Control mainControl) {
		this.mainControl = mainControl;
		if (mainControl != null) {
			mainControl.addDisposeListener(e -> dispose());
		}
	}

	@Override
	public final Control getMainControl() {
		return mainControl;
	}

	@Override
	public Control[] getControls() {
		List<Control> list = new ArrayList<>();
		list.add(mainControl);
		if (relatedControl != null && relatedControl != mainControl) {
			list.add(relatedControl);
		}
		return list.toArray(new Control[0]);
	}

	public void addValidator(
			SwtParameterValidator<PARAMTYPE, VALUETYPE> validator) {
		synchronized (validators) {
			if (!validators.contains(validator)) {
				validators.add(validator);
			}
		}
	}

	public void removeValidator(
			SwtParameterValidator<PARAMTYPE, VALUETYPE> validator) {
		synchronized (validators) {
			validators.remove(validator);
		}
	}

	boolean validating = false;

	public ValidationInfo validate(VALUETYPE newValue) {
		if (validating) {
			return new ValidationInfo(true);
		}
		ValidationInfo resultValidation = new ValidationInfo(true);
		try {
			validating = true;
			//noinspection rawtypes
			SwtParameterValidator[] validators = this.validators.toArray(
					new SwtParameterValidator[0]);
			//noinspection unchecked
			for (SwtParameterValidator<PARAMTYPE, VALUETYPE> validator : validators) {
				ValidationInfo validationInfo = validator.isValidParameterValue(
						thisTyped, newValue);
				if (validationInfo == null) {
					continue;
				}
				if (!validationInfo.valid) {
					resultValidation = validationInfo;
					break;
				} else if (validationInfo.info != null) {
					if (resultValidation.info == null) {
						resultValidation.info = validationInfo.info;
					} else {
						resultValidation.info += "\n" + validationInfo.info;
					}
				}
			}
		} finally {
			validating = false;
		}
		updateControl(resultValidation);
		return resultValidation;
	}

	protected void updateControl(ValidationInfo validationInfo) {
		if (lastValidationInfo == validationInfo) {
			return;
		}
		if (validationInfo == null) {
			lastValidationInfo = null;
			return;
		}

		boolean changed = lastValidationInfo == null
				|| validationInfo.valid != lastValidationInfo.valid
				|| (!Objects.equals(validationInfo.info, lastValidationInfo.info));
		lastValidationInfo = validationInfo;

		if (!changed) {
			return;
		}

		if (mainControl == null || mainControl.isDisposed()) {
			return;
		}
		if (!validationInfo.valid) {
			if (validationInfo.info != null) {
				Utils.execSWTThread(() -> {
					if (mainControl.isDisposed()) {
						return;
					}
					String curTooltip = mainControl.getToolTipText();
					if (oldTooltip == null) {
						oldTooltip = curTooltip == null ? "" : curTooltip;
					}
					mainControl.setToolTipText(validationInfo.info);
				});
			}
		} else {
			if (oldTooltip != null) {
				String tooltip = oldTooltip;
				oldTooltip = null;
				Utils.execSWTThread(() -> {
					if (mainControl.isDisposed()) {
						return;
					}
					mainControl.setToolTipText(tooltip);
				});
			}
		}

		Utils.execSWTThread(() -> {
			if (mainControl.isDisposed()) {
				return;
			}
			// Note: for Spinner in Windows, getBackground always seems to return white
			
			Utils.setSkinnedForeground(mainControl, validationInfo.valid ? null : Colors.colorError);
			Utils.setSkinnedBackground(mainControl, validationInfo.valid ? null : Colors.colorErrorBG);
		});
	}

	public void addAndFireChangeListener(ParameterChangeListener<PARAMTYPE> l) {
		addChangeListener(l);
		if (DEBUG) {
			debug("addAndFireChangeListener. trigger " + l);
		}
		try {
			class_mon.enter();

			try {
				l.parameterChanged(thisTyped);
			} catch (Exception e) {
				Debug.out(e);
			}
		} finally {

			class_mon.exit();
		}
	}

	/**
	 * Note: Change Listener may be triggered off SWT Thread
	 */
	public void addChangeListener(ParameterChangeListener<PARAMTYPE> l) {
		try {
			class_mon.enter();

			if (change_listeners == null) {

				change_listeners = new ArrayList<>(1);
			}

			change_listeners.add(l);

		} finally {

			class_mon.exit();
		}
	}

	public void removeChangeListener(ParameterChangeListener<PARAMTYPE> l) {
		try {
			class_mon.enter();

			if (change_listeners == null) {
				return;
			}

			change_listeners.remove(l);

		} finally {

			class_mon.exit();
		}
	}

	@Override
	public void setEnabled(boolean enabled) {
		if (this.enabled == enabled) {
			return;
		}
		this.enabled = enabled;
		if (DEBUG) {
			debug("Set Enabled " + enabled);
		}

		if (pluginParam != null) {
			pluginParam.setEnabled(enabled);
		}

		Utils.execSWTThread(() -> {
			for (Control c : getControls()) {

				if (!c.isDisposed()) {
					c.setEnabled(enabled);
					//noinspection ConstantConditions
					if (c.getClass().equals(Composite.class)) {
						// for the color of the fancy indent
						c.redraw();
					}
				}
			}
		});
	}

	@Override
	public boolean isDisposed() {
		return (mainControl == null || mainControl.isDisposed());
	}

	@Override
	public final void setValue(VALUETYPE value) {
		ValidationInfo validationInfo = validate(value);
		if (validationInfo != null && !validationInfo.valid) {
			if (DEBUG) {
				debug("Skipping setValue, not valid");
			}
			return;
		}

		if (valueProcessor == null || valueProcessor.setValue(thisTyped, value)) {
			triggerChangeListeners(true);
		}
	}

	protected boolean doGridData(Composite composite) {
		if (composite == null) {
			return false;
		}
		Layout layout = composite.getLayout();
		return (layout instanceof GridLayout)
				&& ((GridLayout) layout).numColumns > 1;
	}

	public void refreshControl() {

		Utils.execSWTThread(() -> {
			if (pluginParam != null) {
				if (relatedControl != null && !relatedControl.isDisposed()) {
					Messages.updateLanguageKey(relatedControl, pluginParam.getLabelKey());
				}

				setVisible(pluginParam.isVisible());
				setEnabled(pluginParam.isEnabled());
			}

		});
	}

	protected void refreshSuffixControl(Control suffixControl) {
		if (suffixControl == null || suffixControl.isDisposed()) {
			return;
		}
		if (pluginParam instanceof ParameterWithSuffix) {
			Messages.updateLanguageKey(suffixControl,
					((ParameterWithSuffix) pluginParam).getSuffixLabelKey());
		}
	}

	@Override
	@Deprecated
	public void setLayoutData(Object layoutData) {
		if (mainControl != null && !mainControl.isDisposed()) {
			mainControl.setLayoutData(layoutData);
		}
	}

	@Override
	public VALUETYPE getValue() {
		if (valueProcessor == null) {
			return null;
		}
		return valueProcessor.getValue(thisTyped);
	}

	@Override
	public void informChanged() {
		triggerChangeListeners(true);
	}

	protected final void triggerChangeListeners(boolean doRefresh) {
		if (DEBUG && !Thread.currentThread().getName().startsWith(
				Utils.THREAD_NAME_OFFSWT)) {
			Object value = getValue();
			if (value instanceof int[]) {
				value = Arrays.toString((int[]) value);
			}
			debug("changed to " + value + ". # listeners="
					+ (change_listeners == null ? "0" : change_listeners.size())
					+ "; doRefresh=" + doRefresh);
		}

		if (Utils.isThisThreadSWT()) {
			Utils.getOffOfSWTThread(() -> triggerChangeListeners(doRefresh));
			return;
		}

		// We don't need to trigger pluginParam.
		// Plugin Parameters add COConfigurationManager.addParameterListener
		// which handles plugin triggers.
		// Parameters that don't use COCOnfigurationManager will override this
		// method to handle passing trigger to pluginParam

		try {
			class_mon.enter();

			if (change_listeners != null) {

				for (ParameterChangeListener<PARAMTYPE> listener : change_listeners) {
					if (DEBUG) {
						debug("changed. trigger " + listener);
					}
					try {
						listener.parameterChanged(thisTyped);
					} catch (Exception e) {
						Debug.out(e);
					}
				}
			}

		} finally {

			class_mon.exit();

		}

		triggerSubClassChangeListeners();

		if (doRefresh) {
			refreshControl();
		}
	}

	protected void triggerSubClassChangeListeners() {
	}

	public Control getRelatedControl() {
		return relatedControl;
	}

	protected void setRelatedControl(Control control) {
		if (relatedControl == control) {
			return;
		}
		relatedControl = control;
		if (control == null || control.isDisposed()) {
			return;
		}
		control.addMenuDetectListener(e -> {
			Control curControl = (Control) e.widget;
			if (curControl == null || curControl.isDisposed()) {
				return;
			}

			Menu menu = new Menu(curControl);

			addLabelContextMenus(curControl, menu);

			if (menu.getItemCount() > 0) {
				//noinspection ConstantConditions
				menu.addListener(SWT.Hide,
						event -> event.display.asyncExec(menu::dispose));
				menu.setVisible(true);
			} else {
				menu.dispose();
			}
		});
		
		control.addListener(
			SWT.MouseHover,
			(e)->{
				if ( pluginParam != null ){
					
					String tt = control.getToolTipText();
					
					if ( tt == null ){
						
						String[] modeKeys = {
								"ConfigView.section.mode.beginner",
								"ConfigView.section.mode.intermediate",
								"ConfigView.section.mode.advanced"
							};
						
						
						control.setToolTipText(
							MessageText.getString( "ConfigView.section.mode" ) + ": " + 
							MessageText.getString( modeKeys[pluginParam.getMinimumRequiredUserMode()]) );
					}
				}
			});
	}

	protected void addLabelContextMenus(Control curControl, Menu menu) {
		if (mainControl != null && mainControl.isFocusControl()) {
			// Force control removes focus from mainControl, ensuring any new
			// value entered by user is processed before showing menu (which may
			// rely on current value)
			curControl.forceFocus();
		}

		if (valueProcessor != null) {
			if (!valueProcessor.isDefaultValue(thisTyped)) {
				Object defaultValue = valueProcessor.getDefaultValue(thisTyped);
				MenuItem item = new MenuItem(menu, SWT.PUSH);
				Messages.setLanguageText(item, "menu.config.reset.to.default");
				if ((defaultValue instanceof String)
						|| (defaultValue instanceof Number)) {
					item.setToolTipText(defaultValue.toString());
				}
				item.addListener(SWT.Selection, event -> resetToDefault());
			} else if (valueProcessor.getDefaultValue(thisTyped) != null) {
				MenuItem item = new MenuItem(menu, SWT.PUSH);
				Messages.setLanguageText(item, "menu.config.is.default");
				item.setEnabled(false);
			}
		}
		String message = Messages.getLanguageForControl( curControl );
		if ( message != null && !message.trim().isEmpty()){
			
			if ( menu.getItemCount() > 0 ){
				new MenuItem( menu, SWT.SEPARATOR );
			}
			
			message = message.trim();
			
			String context = ConfigView.getSectionContext( curControl );
			
			if ( context != null ){
				
				message = context + ": " + message;
			}
			
			String f_message = message;
			
			ClipboardCopy.addCopyToClipMenu(menu, "copy.config.param.name", ()->f_message );
		}
	}

	public boolean resetToDefault() {
		if (valueProcessor == null || valueProcessor.resetToDefault(thisTyped)) {
			informChanged();
			return true;
		}
		return false;
	}

	public boolean isDefaultValue(){
		return( valueProcessor != null && valueProcessor.isDefaultValue(thisTyped));
	}

	protected void setPluginParameter(Parameter pluginParam) {
		this.pluginParam = pluginParam;
		if (pluginParam == null) {
			return;
		}
		if (pluginParam instanceof ParameterImpl) {
			addValidator(
					(p, toValue) -> ((ParameterImpl) this.pluginParam).validate(toValue));
		}
	}

	public void setValueProcessor(
			SwtParameterValueProcessor<PARAMTYPE, VALUETYPE> valueProcessor) {
		this.valueProcessor = valueProcessor;
		refreshControl();
	}

	protected void setConfigValueProcessor(Class<VALUETYPE> type) {
		valueProcessor = new SwtConfigParameterValueProcessor<>(this, paramID,
				type);
		refreshControl();
	}

	public SwtParameterValueProcessor<PARAMTYPE, VALUETYPE> getValueProcessor() {
		return valueProcessor;
	}

	@Override
	public void setVisible(boolean visible) {
		if (DEBUG && !Thread.currentThread().getName().startsWith(
				Utils.THREAD_NAME_OFFSWT)) {
			debug("setVisible " + visible);
		}

		if (Utils.runIfNotSWTThread(() -> setVisible(visible))) {
			return;
		}

		if (mainControl == null || mainControl.isDisposed()) {
			return;
		}
		Control[] controls = getControls();
		for (Control con : controls) {
			Object layoutData = con.getLayoutData();
			if (layoutData instanceof GridData) {
				boolean oldVisible = !((GridData) layoutData).exclude;
				if (oldVisible != visible) {
					((GridData) layoutData).exclude = !visible;
					con.setLayoutData(layoutData);
					try {
						con.requestLayout();
					}catch( Throwable e ) {
						// old swt no support
					}
				}
			}
			con.setVisible(visible);
		}
	}

	@Override
	public void debug(String s) {
		System.out.println(
				Thread.currentThread().getName() + "|" + this + ": " + paramID + "] "
						+ s + "; " + Debug.getCompressedStackTraceSkipFrames(1));
	}

	public int getIndent() {
		return indent;
	}

	public boolean isIndentFancy() {
		return fancyPaintListener != null;
	}

	public void setIndent(int i, boolean fancy) {
		if (Utils.runIfNotSWTThread(() -> setIndent(i, fancy))) {
			return;
		}

		indent = i;
		Control firstControl = relatedControl == null ? mainControl
				: relatedControl;

		if (firstControl == null || firstControl.isDisposed()) {
			return;
		}
		Object data = firstControl.getLayoutData();
		int width = i == 0 ? 0 : fancy ? 35 + (25 * (i - 1)) : i * 18;
		if (data instanceof GridData) {
			((GridData) data).horizontalIndent = width;
			firstControl.setLayoutData(data);
		} else if (doGridData(firstControl.getParent())) {
			GridData gridData = new GridData();
			gridData.horizontalIndent = width;
			firstControl.setLayoutData(gridData);
		}
		if (i == 0) {
			return;
		}
		if (fancy && fancyPaintListener == null) {
			fancyPaintListener = new IndentPaintListener(firstControl, width);
		} else if (!fancy && fancyPaintListener != null) {
			fancyPaintListener.dispose();
			fancyPaintListener = null;
		}
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void dispose() {
		validators.clear();
		change_listeners = null;
		if (valueProcessor != null) {
			valueProcessor.dispose(thisTyped);
			valueProcessor = null;
		}
		Utils.disposeSWTObjects((Object[]) getControls());
	}

	Control createStandardLabel(Composite parent, String labelKey) {
		if (labelKey == null) {
			return null;
		}
		Control label;
		String text = MessageText.getString(labelKey);
		if (text.contains("<a ")) {
			Link link = new Link(parent, SWT.NONE);
			label = link;
			link.addListener(SWT.Selection, event -> Utils.launch(event.text));
		} else {
			label = new Label(parent, SWT.NONE);
		}
		if (parent.getLayout() instanceof GridLayout) {
			// set GridData so setVisible works
			label.setLayoutData(new GridData());
		}
		Messages.setLanguageText(label, labelKey);
		setRelatedControl(label);
		return label;
	}

	public void setLabelKey(String labelKey) {
		Utils.execSWTThread(() -> {
			Control relatedControl = getRelatedControl();
			Messages.updateLanguageKey(relatedControl, labelKey);
		});
	}

	public void setLabelText(String text) {
		Utils.execSWTThread(() -> {
			Control relatedControl = getRelatedControl();
			Messages.updateLanguageKey(relatedControl, "!" + text + "!");
		});
	}

	protected static class IndentPaintListener
		implements Listener
	{
		private final Control control;

		private final int width;

		private final Composite parent;

		/**
		 * Init on SWT Thread
		 */
		public IndentPaintListener(Control control, int width) {
			this.control = control;
			this.width = width;
			parent = control.getParent();
			if (parent == null) {
				return;
			}
			parent.addListener(SWT.Paint, this);
		}

		/**
		 * Any Thread
		 */
		public void dispose() {
			if (parent == null) {
				return;
			}
			Utils.execSWTThread(() -> parent.removeListener(SWT.Paint, this));
		}

		@Override
		public void handleEvent(Event event) {
			if (parent == null || control.isDisposed()) {
				return;
			}

			Rectangle boundsInParent = control.getBounds();
			if (boundsInParent.width == 0 || boundsInParent.height == 0) {
				return;
			}
			Rectangle bounds = control.getBounds();
			GC gc = event.gc;
			Display display = event.display;
			if (gc == null || display == null) {
				return;
			}

			int x = boundsInParent.x - width + 18;
			int x2 = x + width - 22;
			int y = boundsInParent.y;
			int y2 = boundsInParent.y + (bounds.height / 2);
			gc.setLineStyle(SWT.LINE_DASH);
			gc.setLineDash(new int[] {
				3,
				2
			});
			gc.setForeground(display.getSystemColor(parent.isEnabled()
					? SWT.COLOR_WIDGET_FOREGROUND : SWT.COLOR_WIDGET_LIGHT_SHADOW));
			gc.drawLine(x, y, x, y2);
			gc.drawLine(x, y2, x2, y2);

			x++;
			y++;
			y2++;
			x2++;
			gc.setForeground(display.getSystemColor(SWT.COLOR_WIDGET_NORMAL_SHADOW));
			gc.drawLine(x, y, x, y2);
			gc.drawLine(x, y2, x2, y2);

		}
	}
}
