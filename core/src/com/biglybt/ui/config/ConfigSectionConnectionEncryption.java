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

package com.biglybt.ui.config;

import java.util.ArrayList;
import java.util.List;

import com.biglybt.core.util.Wiki;
import com.biglybt.pifimpl.local.ui.config.*;

import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.pif.ui.config.Parameter;
import com.biglybt.pif.ui.config.ParameterListener;

import static com.biglybt.core.config.ConfigKeys.Connection.*;

public class ConfigSectionConnectionEncryption
	extends ConfigSectionImpl
{
	private static final String SECTION_ID = "connection.encryption";

	public ConfigSectionConnectionEncryption() {
		super(SECTION_ID, ConfigSection.SECTION_CONNECTION,
				Parameter.MODE_INTERMEDIATE);
	}

	@Override
	public void build() {

		List<Parameter> listEncrypt = new ArrayList<>();

		add(new LabelParameterImpl(
				"ConfigView.section.connection.encryption.encrypt.info"), listEncrypt);

		add(new HyperlinkParameterImpl(
				"ConfigView.section.connection.encryption.encrypt.info.link",
				Wiki.AVOID_TRAFFIC_SHAPING), listEncrypt);

		BooleanParameterImpl paramEncryptRequire = new BooleanParameterImpl(
				BCFG_NETWORK_TRANSPORT_ENCRYPTED_REQUIRE,
				"ConfigView.section.connection.encryption.require_encrypted_transport");
		add(paramEncryptRequire, listEncrypt);

		String[] encryption_types = {
			"Plain",
			"RC4"
		};
		String[] dropLabels = new String[encryption_types.length];
		String[] dropValues = new String[encryption_types.length];
		for (int i = 0; i < encryption_types.length; i++) {
			dropLabels[i] = encryption_types[i];
			dropValues[i] = encryption_types[i];
		}

		StringListParameterImpl paramMinLevel = new StringListParameterImpl(
				SCFG_NETWORK_TRANSPORT_ENCRYPTED_MIN_LEVEL,
				"ConfigView.section.connection.encryption.min_encryption_level",
				dropLabels, dropValues);
		add(paramMinLevel, listEncrypt);

		LabelParameterImpl paramFallBackInfo = new LabelParameterImpl(
				"ConfigView.section.connection.encryption.encrypt.fallback_info");
		add(paramFallBackInfo, listEncrypt);

		BooleanParameterImpl paramFallbackOutgoing = new BooleanParameterImpl(
				BCFG_NETWORK_TRANSPORT_ENCRYPTED_FALLBACK_OUTGOING,
				"ConfigView.section.connection.encryption.encrypt.fallback_outgoing");
		add(paramFallbackOutgoing, listEncrypt);

		BooleanParameterImpl paramFallbackIncoming = new BooleanParameterImpl(
				BCFG_NETWORK_TRANSPORT_ENCRYPTED_FALLBACK_INCOMING,
				"ConfigView.section.connection.encryption.encrypt.fallback_incoming");
		add(paramFallbackIncoming, listEncrypt);

		BooleanParameterImpl paramUseCryptoPort = new BooleanParameterImpl(
				BCFG_NETWORK_TRANSPORT_ENCRYPTED_USE_CRYPTO_PORT,
				"ConfigView.section.connection.encryption.use_crypto_port");
		add(paramUseCryptoPort, listEncrypt);

		final Parameter[] ap_controls = {
			paramMinLevel,
			paramFallBackInfo,
			paramFallbackOutgoing,
			paramFallbackIncoming
		};

		ParameterListener iap = param -> {
			boolean required = paramEncryptRequire.getValue();

			boolean ucp_enabled = !paramFallbackIncoming.getValue() && required;

			paramUseCryptoPort.setEnabled(ucp_enabled);

			for (Parameter requireParams : ap_controls) {
				requireParams.setEnabled(required);
			}
		};
		paramFallbackIncoming.addListener(iap);

		paramEncryptRequire.addListener(iap);
		iap.parameterChanged(null);

		add(new ParameterGroupImpl(
				"ConfigView.section.connection.encryption.encrypt.group", listEncrypt));

	}
}
