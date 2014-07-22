/**
 *  @author Shaun
 *  @date 4/2/14
 *  @copyright 2014 Mocha. All rights reserved.
 */
package mocha.orm;

import android.util.Pair;
import mocha.foundation.Copying;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Query implements Copying<Query> {
	enum CombineOperator {
		AND(" && "), OR(" || ");

		final String value;

		private CombineOperator(String value) {
			this.value = value;
		}
	}

	enum ComparisonOperator {
		EQ(" == "),
		NE(" <> "),
		LT(" < "),
		LTE(" <= "),
		GT(" > "),
		GTE(" >= "),
		IN(" IN ");

		final String value;

		private ComparisonOperator(String value) {
			this.value = value;
		}
	}

	abstract static class Condition {
		CombineOperator combineOperator;

		Condition(CombineOperator combineOperator) {
			this.combineOperator = combineOperator;
		}
	}

	abstract static class ComparisonCondition extends Condition {
		String property;
		ComparisonOperator comparisonOperator;

		protected ComparisonCondition(CombineOperator combineOperator, String property, ComparisonOperator comparisonOperator) {
			super(combineOperator);
			this.property = property;
			this.comparisonOperator = comparisonOperator;
		}
	}

	final static class SimpleComparisonCondition extends ComparisonCondition {
		Object value;

		SimpleComparisonCondition(CombineOperator combineOperator, String property, ComparisonOperator comparisonOperator, Object value) {
			super(combineOperator, property, comparisonOperator);
			this.value = value;
		}

		public String toString() {
			return String.format("%s (%s %s %s)", this.combineOperator, this.property, this.comparisonOperator, this.value);
		}
	}

	final static class AdvancedComparisonCondition extends ComparisonCondition {
		List<Object> values;

		AdvancedComparisonCondition(CombineOperator combineOperator, String property, ComparisonOperator comparisonOperator, List<Object> values) {
			super(combineOperator, property, comparisonOperator);
			this.values = values;
		}

		public String toString() {
			return String.format("%s (%s %s %s)", this.combineOperator, this.property, this.comparisonOperator, this.values);
		}
	}

	final static class CompoundQueryCondition extends Condition {
		Query compoundQuery;

		CompoundQueryCondition(CombineOperator combineOperator, Query compoundQuery) {
			super(combineOperator);
			this.compoundQuery = compoundQuery.copy();
		}

		public String toString() {
			return String.format("%s (%s)", this.combineOperator, this.compoundQuery);
		}
	}


	List<Condition> conditions = new ArrayList<>();

	public Query() {

	}

	public String toString() {
		return this.conditions.toString();
	}

	public Query eq(String property, Object value) {
		this.conditions.add(new SimpleComparisonCondition(CombineOperator.AND, property, ComparisonOperator.EQ, value));
		return this;
	}

	public Query orEq(String property, Object value) {
		this.conditions.add(new SimpleComparisonCondition(CombineOperator.OR, property, ComparisonOperator.EQ, value));
		return this;
	}

	public Query notEq(String property, Object value) {
		this.conditions.add(new SimpleComparisonCondition(CombineOperator.AND, property, ComparisonOperator.NE, value));
		return this;
	}

	public Query orNotEq(String property, Object value) {
		this.conditions.add(new SimpleComparisonCondition(CombineOperator.OR, property, ComparisonOperator.NE, value));
		return this;
	}

	public Query lt(String property, Object value) {
		this.conditions.add(new SimpleComparisonCondition(CombineOperator.AND, property, ComparisonOperator.LT, value));
		return this;
	}

	public Query orLt(String property, Object value) {
		this.conditions.add(new SimpleComparisonCondition(CombineOperator.OR, property, ComparisonOperator.LT, value));
		return this;
	}

	public Query lte(String property, Object value) {
		this.conditions.add(new SimpleComparisonCondition(CombineOperator.AND, property, ComparisonOperator.LTE, value));
		return this;
	}

	public Query orLte(String property, Object value) {
		this.conditions.add(new SimpleComparisonCondition(CombineOperator.OR, property, ComparisonOperator.LTE, value));
		return this;
	}

	public Query gt(String property, Object value) {
		this.conditions.add(new SimpleComparisonCondition(CombineOperator.AND, property, ComparisonOperator.GT, value));
		return this;
	}

	public Query orGt(String property, Object value) {
		this.conditions.add(new SimpleComparisonCondition(CombineOperator.OR, property, ComparisonOperator.GT, value));
		return this;
	}

	public Query gte(String property, Object value) {
		this.conditions.add(new SimpleComparisonCondition(CombineOperator.AND, property, ComparisonOperator.GTE, value));
		return this;
	}

	public Query orGte(String property, Object value) {
		this.conditions.add(new SimpleComparisonCondition(CombineOperator.OR, property, ComparisonOperator.GTE, value));
		return this;
	}

	public Query in(String property, List<Object> values) {
		if(values.size() > 0) {
			this.conditions.add(new AdvancedComparisonCondition(CombineOperator.AND, property, ComparisonOperator.IN, values));
		}

		return this;
	}

	public Query in(String property, Object... values) {
		return this.in(property, Arrays.asList(values));
	}

	public Query orIn(String property, List<Object> values) {
		if(values.size() > 0) {
			this.conditions.add(new AdvancedComparisonCondition(CombineOperator.OR, property, ComparisonOperator.IN, values));
		}

		return this;
	}

	public Query orIn(String property, Object... values) {
		return this.orIn(property, Arrays.asList(values));
	}

	public Query and(Query compoundQuery) {
		this.conditions.add(new CompoundQueryCondition(CombineOperator.AND, compoundQuery));
		return this;
	}

	public Query or(Query compoundQuery) {
		this.conditions.add(new CompoundQueryCondition(CombineOperator.OR, compoundQuery));
		return this;
	}

	Pair<String, List<String>> compile(ModelEntity entity, String table) {
		StringBuilder query = new StringBuilder();
		List<String> bindings = new ArrayList<>();

		boolean first = true;

		for(Condition condition : this.conditions) {
			if(condition instanceof ComparisonCondition) {
				String column = entity.getColumnForFieldName(((ComparisonCondition)condition).property);
				if(column == null) continue;

				if(!first) {
					query.append(condition.combineOperator.value);
				}

				query.append(table).append(".").append(column).append(((ComparisonCondition)condition).comparisonOperator.value);

				if(condition instanceof SimpleComparisonCondition) {
					query.append("?");
					bindings.add(this.toBindingString(((SimpleComparisonCondition)condition).value));
				} else if(condition instanceof AdvancedComparisonCondition) {
					query.append("(");

					boolean firstValue = true;

					for(Object value : ((AdvancedComparisonCondition) condition).values) {
						if(firstValue) {
							query.append("?");
							firstValue = false;
						} else {
							query.append(",?");
						}

						bindings.add(this.toBindingString(value));
					}

					query.append(")");
				} else {
					throw new UnsupportedConditionException(condition);
				}
			} else if(condition instanceof CompoundQueryCondition) {
				Pair<String, List<String>> pair = ((CompoundQueryCondition) condition).compoundQuery.compile(entity, table);

				if(pair.first != null && pair.first.length() > 0) {
					if(!first) {
						query.append(condition.combineOperator.value);
					}

					query.append("(").append(pair.first).append(")");
					bindings.addAll(pair.second);
				}
			} else {
				throw new UnsupportedConditionException(condition);
			}

			first = false;
		}

		return new Pair<>(query.toString(), bindings);
	}

	private String toBindingString(Object value) {
		if(value != null) {
			if (value instanceof Model) {
				return String.valueOf(((Model) value).primaryKey);
			} else {
				return value.toString();
			}
		} else {
			return null;
		}
	}

	public Query copy() {
		Query query = new Query();
		query.conditions.addAll(this.conditions);
		return query;
	}

	// It shouldn't be possible for this to actually ever be thrown.
	public class UnsupportedConditionException extends RuntimeException {
		public UnsupportedConditionException(Condition condition) {
			super("Unsupported condition: " + condition);
		}
	}

}

