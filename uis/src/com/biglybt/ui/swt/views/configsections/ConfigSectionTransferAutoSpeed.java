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

package com.biglybt.ui.swt.views.configsections;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.components.LinkLabel;
import com.biglybt.ui.swt.config.*;
import com.biglybt.ui.swt.pif.UISWTConfigSection;



public class ConfigSectionTransferAutoSpeed implements UISWTConfigSection {

	@Override
	public String configSectionGetParentSection() {
        return "transfer.select";
    }

	@Override
	public String configSectionGetName() {
		return "transfer.autospeed";
	}

	@Override
	public void configSectionSave() {
	}

	@Override
	public void configSectionDelete() {
	}

	@Override
	public int maxUserMode() {
		return 2;
	}


	@Override
	public Composite configSectionCreate(final Composite parent) {
		GridData gridData;

		Composite cSection = new Composite(parent, SWT.NULL);

		gridData = new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL);
		cSection.setLayoutData(gridData);
		GridLayout advanced_layout = new GridLayout();
		advanced_layout.numColumns = 2;
		cSection.setLayout(advanced_layout);

		int userMode = COConfigurationManager.getIntParameter("User Mode");


		Label linfo = new Label(cSection, SWT.WRAP);
		Messages.setLanguageText( linfo, "ConfigView.section.transfer.autospeed.info" );
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.horizontalSpan = 2;
		linfo.setLayoutData(gridData);

		gridData = new GridData();
		gridData.horizontalSpan = 2;
		new LinkLabel(cSection, gridData, "ConfigView.label.please.visit.here",
				Constants.URL_WIKI + "w/Auto_Speed");


		String[]	units = { DisplayFormatters.getRateUnit( DisplayFormatters.UNIT_KB )};

			// min up

		Label llmux = new Label(cSection, SWT.NULL);
		Messages.setLanguageText( llmux, "ConfigView.section.transfer.autospeed.minupload", units );
		IntParameter min_upload = new IntParameter(cSection,
				"AutoSpeed Min Upload KBs");
		gridData = new GridData();
		min_upload.setLayoutData(gridData);

			// max up

		Label llmdx = new Label(cSection, SWT.NULL);
		Messages.setLanguageText( llmdx, "ConfigView.section.transfer.autospeed.maxupload", units );
		IntParameter max_upload = new IntParameter(cSection,
				"AutoSpeed Max Upload KBs");
		gridData = new GridData();
		max_upload.setLayoutData(gridData);


        if ( userMode > 0 ){

			BooleanParameter enable_down_adj = new BooleanParameter(
					cSection, "AutoSpeed Download Adj Enable",
					"ConfigView.section.transfer.autospeed.enabledownadj" );
			gridData = new GridData();
			gridData.horizontalSpan = 2;
			enable_down_adj.setLayoutData(gridData);


			Label label = new Label(cSection, SWT.NULL);
			Messages.setLanguageText( label, "ConfigView.section.transfer.autospeed.downadjratio" );

			FloatParameter down_adj = new FloatParameter( cSection, "AutoSpeed Download Adj Ratio", 0, Float.MAX_VALUE, false, 2  );
			gridData = new GridData();
			down_adj.setLayoutData(gridData);


			enable_down_adj.setAdditionalActionPerformer(
		    		new ChangeSelectionActionPerformer( new Control[]{ down_adj.getControl()}));
		}

		if ( userMode > 1 ){

				// max inc

			Label label = new Label(cSection, SWT.NULL);
			Messages.setLanguageText( label, "ConfigView.section.transfer.autospeed.maxinc", units );

			final IntParameter max_increase = new IntParameter(cSection,
					"AutoSpeed Max Increment KBs");
			gridData = new GridData();
			max_increase.setLayoutData(gridData);

				// max dec

			label = new Label(cSection, SWT.NULL);
			Messages.setLanguageText( label, "ConfigView.section.transfer.autospeed.maxdec", units );

			final IntParameter max_decrease = new IntParameter(cSection,
					"AutoSpeed Max Decrement KBs");
			gridData = new GridData();
			max_decrease.setLayoutData(gridData);


				// choking ping

			label = new Label(cSection, SWT.NULL);
			Messages.setLanguageText( label, "ConfigView.section.transfer.autospeed.chokeping" );

			final IntParameter choke_ping = new IntParameter(cSection,
					"AutoSpeed Choking Ping Millis");
			gridData = new GridData();
			choke_ping.setLayoutData(gridData);

				// forced min

			label = new Label(cSection, SWT.NULL);
			Messages.setLanguageText( label, "ConfigView.section.transfer.autospeed.forcemin", units );

			final IntParameter forced_min = new IntParameter(cSection,
					"AutoSpeed Forced Min KBs");
			gridData = new GridData();
			forced_min.setLayoutData(gridData);

				// latency

			label = new Label(cSection, SWT.NULL);
			Messages.setLanguageText( label, "ConfigView.section.transfer.autospeed.latencyfactor" );

			final IntParameter latency_factor = new IntParameter(cSection,
					"AutoSpeed Latency Factor", 1, Integer.MAX_VALUE);
			gridData = new GridData();
			latency_factor.setLayoutData(gridData);

		    Label reset_label = new Label(cSection, SWT.NULL );
		    Messages.setLanguageText(reset_label, "ConfigView.section.transfer.autospeed.reset");

		    Button reset_button = new Button(cSection, SWT.PUSH);

		    Messages.setLanguageText(reset_button, "ConfigView.section.transfer.autospeed.reset.button" );

		    reset_button.addListener(SWT.Selection,
		    		new Listener()
					{
				        @Override
				        public void
						handleEvent(Event event)
				        {
				        	max_increase.resetToDefault();
				        	max_decrease.resetToDefault();
				        	choke_ping.resetToDefault();
				        	latency_factor.resetToDefault();
				        	forced_min.resetToDefault();
				        }
				    });
		}

		return cSection;

	}

}
