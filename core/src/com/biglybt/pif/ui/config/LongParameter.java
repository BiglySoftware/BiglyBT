package com.biglybt.pif.ui.config;

public interface
LongParameter
	extends Parameter, ParameterWithSuffix
{
	public long
	getValue();

	public void
	setValue(
			long	v );

	boolean isLimited();

	long getMinValue();

	long getMaxValue();

	void setMinValue(long min_value);

	void setMaxValue(long max_value);

	void addLongValidator(ParameterValidator<Long> validator);
}
