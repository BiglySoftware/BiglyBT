package com.biglybt.pifimpl.local.ui.config;

import com.biglybt.core.config.COConfigurationManager;

import com.biglybt.pif.ui.config.LongParameter;
import com.biglybt.pif.ui.config.ParameterValidator;

public class LongParameterImpl
	extends ParameterImpl
	implements LongParameter
{
	private boolean limited;
	private long min_value;
	private long max_value = Integer.MAX_VALUE;
	private boolean storedAsString;
	private long valueWhenBlank;
	private String suffixLabelKey;

	public LongParameterImpl(String configKey, String labelKey) {
		super(configKey, labelKey);

		this.limited = false;
	}

	public LongParameterImpl(String configKey, String labelKey, long min_value,
			long max_value) {
		this(configKey, labelKey);
		this.min_value = min_value;
		this.max_value = max_value;
		this.limited = true;
	}


	@Override
	public long
	getValue()
	{
		if (!storedAsString) {
			return COConfigurationManager.getLongParameter(configKey);
		}

		String valFromConfig = COConfigurationManager.getStringParameter(configKey);
		long val = valueWhenBlank;
		try {
			if (!valFromConfig.isEmpty()) {
				val = Long.parseLong(valFromConfig);
			}
		} catch (Exception ignore) {
		}
		return val;
	}

	@Override
	public Object getValueObject() {
		return getValue();
	}

	@Override
	public void
	setValue(
		long	value )
	{
		if (!storedAsString) {
			COConfigurationManager.setParameter(configKey, value);
		} else {
			COConfigurationManager.setParameter(configKey, "" + value);
		}
	}

	@Override
	public boolean isLimited() {return limited;}
	@Override
	public long getMinValue() {return this.min_value;}
	@Override
	public long getMaxValue() {return this.max_value;}

	@Override
	public void setMinValue(long min_value) {
		limited = true;
		this.min_value = min_value;
		refreshControl();
	}

	@Override
	public void setMaxValue(long max_value) {
		limited = true;
		this.max_value = max_value;
		refreshControl();
	}

	@Override
	public void addLongValidator(ParameterValidator<Long> validator) {
		super.addValidator(validator);
	}

	public boolean isStoredAsString() {
		return storedAsString;
	}

	/**
	 * Note: We can't easily propogate this to {@link IntParameter}, because
	 * {@link com.biglybt.pif.ui.model.BasicPluginConfigModel#addIntParameter2(String, String, int)}
	 * sets the default value to int before setStoredAsString can be called. This
	 * introducing a window where retrieving the parameter value may cause a cast
	 * error.
	 * <p/>
	 * Plus, we don't want to encourage plugins to store ints as strings.
	 */
	public void setStoredAsString(boolean storedAsString, int valueWhenBlank) {
		this.storedAsString = storedAsString;
		this.valueWhenBlank = valueWhenBlank;
	}

	public long getValueWhenBlank() {
		return valueWhenBlank;
	}

	@Override
	public String getSuffixLabelKey() {
		return suffixLabelKey;
	}

	@Override
	public void setSuffixLabelKey(String suffixLabelKey) {
		this.suffixLabelKey = suffixLabelKey;
		refreshControl();
	}

	@Override
	public void setSuffixLabelText(String text) {
		this.suffixLabelKey = "!" + text + "!";
		refreshControl();
	}
}
