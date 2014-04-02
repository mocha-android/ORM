/**
 *  @author Shaun
 *  @date 4/1/14
 *  @copyright 2014 Mocha. All rights reserved.
 */
package mocha.orm;

public abstract class Transformer<O,T> {

	public abstract Class<? extends O> getValueClass();
	public abstract Class<? extends T> getTransformedValueClass();

	public abstract ColumnType getColumnType();

	abstract public T getTransformedValue(O value);
	abstract public O getReverseTransformedValue(T transformedValue);

	public static class Date extends Transformer<java.util.Date, Long> {

		public Class<? extends java.util.Date> getValueClass() {
			return java.util.Date.class;
		}

		public Class<? extends Long> getTransformedValueClass() {
			return Long.class;
		}

		public ColumnType getColumnType() {
			return ColumnType.INTEGER;
		}

		public Long getTransformedValue(java.util.Date value) {
			return value.getTime();
		}

		public java.util.Date getReverseTransformedValue(Long transformedValue) {
			return new java.util.Date(transformedValue);
		}
	}

	public static class Calendar extends Transformer<java.util.Calendar, Long> {

		public Class<? extends java.util.Calendar> getValueClass() {
			return java.util.Calendar.class;
		}

		public Class<? extends Long> getTransformedValueClass() {
			return Long.class;
		}

		public ColumnType getColumnType() {
			return ColumnType.INTEGER;
		}

		public Long getTransformedValue(java.util.Calendar value) {
			return value.getTimeInMillis();
		}

		public java.util.Calendar getReverseTransformedValue(Long transformedValue) {
			java.util.Calendar calendar = java.util.Calendar.getInstance();
			calendar.setTimeInMillis(transformedValue);

			return calendar;
		}

	}

}
