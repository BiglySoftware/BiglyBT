/*
 * Created on May 24, 2010
 * Created by Paul Gardner
 *
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


package com.biglybt.ui.swt.beta;


import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Constants;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.components.LinkLabel;
import com.biglybt.ui.swt.wizard.AbstractWizardPanel;
import com.biglybt.ui.swt.wizard.IWizardPanel;

public class
BetaWizardStart
	extends AbstractWizardPanel<BetaWizard>
{
	protected
	BetaWizardStart(
		BetaWizard		wizard )
	{
		super( wizard, null );
	}

	@Override
	public void
	show()
	{
		wizard.setTitle(MessageText.getString( "beta.wizard.intro.title" ));
        wizard.setCurrentInfo( "" );
        wizard.setPreviousEnabled(false);
        wizard.setNextEnabled(false);
        wizard.setFinishEnabled(true);

        Composite rootPanel = wizard.getPanel();
		GridLayout layout = new GridLayout();
		layout.numColumns = 1;
		rootPanel.setLayout(layout);


		Label info_label = new Label( rootPanel, SWT.WRAP );
		GridData gridData = new GridData(GridData.FILL_HORIZONTAL);
		info_label.setLayoutData(gridData);
		info_label.setText( MessageText.getString( "beta.wizard.info" ));

		LinkLabel link = new LinkLabel( rootPanel, "beta.wizard.link", MessageText.getString( "beta.wizard.link.url" ));
		Label link_label = link.getlabel();

		gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.verticalIndent=10;
		link_label.setLayoutData(gridData);

		final Composite gRadio = new Composite(rootPanel, SWT.NULL);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
	    gridData.verticalIndent=10;
		gRadio.setLayoutData(gridData);
		layout = new GridLayout();
		layout.numColumns = 1;
		gRadio.setLayout( layout );


		Button off_button = new Button (gRadio, SWT.RADIO);
		Messages.setLanguageText(off_button, "beta.wizard.off");
		final Button on_button = new Button (gRadio, SWT.RADIO);
		Messages.setLanguageText(on_button, "beta.wizard.on");

		SelectionAdapter l = new SelectionAdapter()
    	{
    		@Override
		    public void
    		widgetSelected(
    			SelectionEvent arg0 )
    		{
    			wizard.setBetaEnabled( on_button.getSelection());
    		}
    	};
		off_button.addSelectionListener(l);
		on_button.addSelectionListener(l);

		on_button.setSelection( wizard.getBetaEnabled());
		off_button.setSelection( !wizard.getBetaEnabled());

		LinkLabel forum = new LinkLabel( rootPanel, "beta.wizard.forum", MessageText.getString( "beta.wizard.forum.url" ));
		Label forum_label = link.getlabel();

		gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.verticalIndent=10;
		forum_label.setLayoutData(gridData);

		Label version_label = new Label( rootPanel, SWT.WRAP );
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.verticalIndent=10;
		version_label.setLayoutData(gridData);
		version_label.setText( MessageText.getString( "beta.wizard.version", new String[]{ Constants.BIGLYBT_VERSION } ));
	}

	@Override
	public IWizardPanel
	getFinishPanel()
	{
		return( this );
	}

	@Override
	public void
	finish()
	{
		wizard.finish();
	}
}
