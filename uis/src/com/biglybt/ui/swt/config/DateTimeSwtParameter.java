/*
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.ui.swt.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.core.util.SystemTime;
import com.biglybt.pifimpl.local.ui.config.DateTimeParameterImpl;
import com.biglybt.ui.swt.DateWindow;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;

public class 
DateTimeSwtParameter
	extends BaseSwtParameter<DateTimeSwtParameter, Long>
{
	private Composite area;

	private Label	valueLabel;
	
	private Button	setButton;
	private Button	clearButton;
		
	protected String keyDialogTitle = null;
	
	protected String keyDialogMessage = null;
	
	public 
	DateTimeSwtParameter(
		Composite				composite,
		DateTimeParameterImpl	param) 
	{
		this(composite, param.getConfigKeyName(), param.getLabelKey(), null );
		
		keyDialogTitle = param.getKeyDialogTitle();
		
		keyDialogMessage = param.getKeyDialogMessage();
	}

	private 
	DateTimeSwtParameter(
		Composite	composite, 
		String		paramID,
		String		labelKey,
		SwtParameterValueProcessor<DateTimeSwtParameter, Long> valueProcessor)
	{
		super( paramID );
		
		createStandardLabel(composite, labelKey);

		area = new Composite(composite, SWT.NONE);
		
		GridLayout gridLayout = new GridLayout(4, false);
		gridLayout.marginHeight = gridLayout.marginWidth = 0;
		area.setLayout(gridLayout);
		if (doGridData(composite)) {
			area.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		}

		valueLabel = new Label( area, SWT.NULL );
		
		GridData gridData = new GridData();
		gridData.widthHint = 150;
		valueLabel.setLayoutData(gridData);
				
		setButton = new Button(area, SWT.PUSH);
		
		Messages.setLanguageText( setButton, "Button.set" );
		
		clearButton = new Button(area, SWT.PUSH);
		
		Messages.setLanguageText( clearButton, "Button.clear" );

		setButton.addListener(SWT.Selection, event -> {
			openDialog(composite.getShell());
		});
		
		clearButton.addListener(SWT.Selection, event -> {
			setValue(0L);
		});
		
		Utils.makeButtonsEqualWidth( Arrays.asList( setButton, clearButton ), 0 );
		
		Label pad = new Label( area, SWT.NULL );
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		pad.setLayoutData(gridData);
		
		if (valueProcessor == null) {
			setConfigValueProcessor(Long.class);
		} else {
			setValueProcessor(valueProcessor);
		}
		
		addChangeListener((p)->{
			
			setValueLabel( getValue());
		});
		
		setValueLabel( getValue());
	}

	private void
	setValueLabel(
		Long	time )
	{
		Utils.execSWTThread(()->{
			if ( time == null || time <= 0 ){
				clearButton.setEnabled( false );
				valueLabel.setText( MessageText.getString( "label.none" ));
			}else{
				clearButton.setEnabled( true );
				valueLabel.setText( DisplayFormatters.formatDateYMDHMS(time));
			}
		});
	}
	
	@Override
	public void 
	setLayoutData(
		Object layoutData) 
	{
		// don't set layout data
	}

	@Override
	public Control[] 
	getControls() 
	{
		List<Control> list = new ArrayList<>(Arrays.asList(super.getControls()));
		list.add(valueLabel);
		list.add(clearButton);
		list.add(setButton);
		list.add(area);
		return list.toArray(new Control[0]);
	}

	private void 
	openDialog(
		Shell		shell )
	{
		long time = getValue();
		
		if ( time <= 0 ){
			
			time = SystemTime.getCurrentTime();
		}
		
		new DateWindow(
			keyDialogTitle,
			time,
			true,
			(v)->{
				setValue( v );
			});
	}

	public void setKeyDialogTitle(String keyDialogTitle) {
		this.keyDialogTitle = keyDialogTitle;
	}

	public void setKeyDialogMessage(String keyDialogMessage) {
		this.keyDialogMessage = keyDialogMessage;
	}

}
