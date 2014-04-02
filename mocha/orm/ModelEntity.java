/**
 *  @author Shaun
 *  @date 4/1/14
 *  @copyright 2014 Mocha. All rights reserved.
 */
package mocha.orm;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import mocha.foundation.MObject;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

class ModelEntity <E extends Model> extends MObject {
	private Store store;
	private Class<E> modelClass;
	private String table;

	private List<String> columns;
	private Map<Field, String> fieldToColumnMap;
	private Map<String, String> fieldNameToColumnMap;
	private Map<String, Field> columnToFieldMap;

	public static final String PRIMARY_KEY_COLUMN = "Z_ID";
	private static final Field PRIMARY_KEY_FIELD = Model.getPrimaryKeyField();

	private SQLiteStatement insertStatement;
	private SQLiteStatement updateStatement;
	private SQLiteStatement deleteStatement;

	public ModelEntity(Store store, Class<E> modelClass) {
		this.store = store;
		this.modelClass = modelClass;
		this.table = "Z" + modelClass.getSimpleName().toUpperCase();

		List<Field> fields = new ArrayList<Field>();

		Class current = modelClass;
		while(current != null && !current.equals(Model.class)) {
			Collections.addAll(fields, current.getDeclaredFields());
			current = current.getSuperclass();
		}

		int fieldSize = fields.size();

		this.columns = new ArrayList<String>(fieldSize);
		this.fieldToColumnMap = new HashMap<Field, String>(fieldSize);
		this.fieldNameToColumnMap = new HashMap<String, String>(fieldSize);
		this.columnToFieldMap = new HashMap<String, Field>(fieldSize);

		for (Field field : fields) {
			if (!Modifier.isPublic(field.getModifiers())) continue;
			if (field.isAnnotationPresent(Transient.class)) continue;

			if (field.isAnnotationPresent(Column.class)) {
				Column column = field.getAnnotation(Column.class);
				Class<? extends Transformer> transformer = column.transformer();

				if(transformer != null && transformer != Transformer.NONE.class) {
					this.store.registerTransformer(transformer);
				}
			}

			String fieldName = field.getName();
			String column = "Z" + fieldName.toUpperCase();

			this.columns.add(column);
			this.fieldToColumnMap.put(field, column);
			this.fieldNameToColumnMap.put(fieldName, column);
			this.columnToFieldMap.put(column, field);
		}

	}

	public void create(SQLiteDatabase database) {
		StringBuilder builder = new StringBuilder("CREATE TABLE IF NOT EXISTS ");
		builder.append("\"").append(this.table).append("\" (");
		builder.append("\"").append(PRIMARY_KEY_COLUMN).append("\" ").append(ColumnType.INTEGER.name()).append(" PRIMARY KEY AUTOINCREMENT");

		List<String> indexes = new ArrayList<String>();
		indexes.add(String.format("CREATE INDEX IF NOT EXISTS %s_%s_INDEX ON %s (%s)", this.table, PRIMARY_KEY_COLUMN, this.table, PRIMARY_KEY_COLUMN));

		for(Map.Entry<Field, String> fieldStringEntry : this.fieldToColumnMap.entrySet()) {
			Field field = fieldStringEntry.getKey();
			String columnName = fieldStringEntry.getValue();
			ColumnType type = this.store.getColumnType(field);

			builder.append(", ").append("\"").append(columnName).append("\" ").append(type);
			String index = null;

			if (field.isAnnotationPresent(Column.class)) {
				Column column = field.getAnnotation(Column.class);

				if(column.indexAsc()) {
					index = " ASC";
				} else if(column.indexDesc()) {
					index = " DESC";
				} else if(column.index()) {
					index = "";
				}

				if (column.length() > -1) {
					builder.append("(").append(column.length()).append(")");
				}

				if (column.notNull()) {
					builder.append(" NOT NULL");
				}

				if (column.unique()) {
					builder.append(" UNIQUE");
				}
			}

			if(Model.class.isAssignableFrom(field.getType())) {
				@SuppressWarnings("unchecked")
				ModelEntity entity = this.store.getModelEntity((Class<? extends Model>)field.getType());
				builder.append(" REFERENCES ").append(entity.getTable()).append("(").append(PRIMARY_KEY_FIELD).append(")");
				index = "";
			}

			if(index != null) {
				indexes.add(String.format("CREATE INDEX IF NOT EXISTS %s_%s_INDEX ON %s (%s%s)", this.table, columnName, this.table, columnName, index));
			}
		}

		builder.append(")");

		database.execSQL(builder.toString());


		for(String index : indexes) {
			database.execSQL(index);
		}
	}

	public String getTable() {
		return this.table;
	}

	public String getColumnForFieldName(String fieldName) {
		return this.fieldNameToColumnMap.get(fieldName);
	}

	public void save(E model) {
		if(model.primaryKey > 0) {
			this.update(model);
		} else {
			this.insert(model);
		}
	}

	private void insert(E model) {
		if(this.insertStatement == null) {
			StringBuilder builder = new StringBuilder("INSERT INTO ");
			builder.append("\"").append(this.table).append("\" (");

			int lastColumnIndex = this.columns.size() - 1;
			String lastColumn = this.columns.get(lastColumnIndex);

			for(String column : this.columns) {
				builder.append("\"").append(column);

				if(column.equals(lastColumn)) {
					builder.append("\")");
				} else {
					builder.append("\",");
				}
			}

			builder.append(" VALUES(");

			for(int i = 0; i <= lastColumnIndex; i++) {
				if(i == lastColumnIndex) {
					builder.append("?");
				} else {
					builder.append("?,");
				}
			}

			builder.append(")");

			this.insertStatement = this.store.compileStatement(builder.toString());
		}

		if(model != null) {
			int columnIndex = 0;
			for (String column : this.columns) {
				this.store.bind(this.insertStatement, ++columnIndex, model, this.columnToFieldMap.get(column));
			}

			model.primaryKey = this.insertStatement.executeInsert();
		}
	}

	private void update(E model) {
		if(this.updateStatement == null) {
			StringBuilder builder = new StringBuilder("UPDATE ");
			builder.append("\"").append(this.table).append("\" SET ");

			int lastColumnIndex = this.columns.size() - 1;
			String lastColumn = this.columns.get(lastColumnIndex);

			for(String column : this.columns) {
				builder.append("\"").append(column).append("\" = ?");

				if(!column.equals(lastColumn)) {
					builder.append(",");
				}
			}


			builder.append("WHERE \"").append(PRIMARY_KEY_COLUMN).append("\" = ?");

			this.updateStatement = this.store.compileStatement(builder.toString());
		}

		int columnIndex = 0;

		for(String column : this.columns) {
			this.store.bind(this.updateStatement, ++columnIndex, model, this.columnToFieldMap.get(column));
		}

		this.store.bind(this.updateStatement, ++columnIndex, model, PRIMARY_KEY_FIELD);

		this.updateStatement.executeUpdateDelete();
	}

	public void delete(E model) {
		if(model.primaryKey <= 0) return;

		if(this.deleteStatement == null) {
			this.deleteStatement = this.store.compileStatement(String.format("DELETE FROM \"%s\" WHERE \"%s\" = ?", this.table, PRIMARY_KEY_FIELD));
		}

		this.deleteStatement.bindLong(1, model.primaryKey);
		this.deleteStatement.executeUpdateDelete();
	}

	public E parseCursor(Cursor cursor, List<String> selectedColumns) {
		if(selectedColumns == null) {
			selectedColumns = this.columns;
		}

		E model;

		try {
			model = this.modelClass.newInstance();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		int selectedColumnsSize = selectedColumns.size();
		for(int i = 0; i < selectedColumnsSize; i++) {
			String column = selectedColumns.get(i);
			Field field = this.columnToFieldMap.get(column);

			if(field != null) {
				this.store.setField(model, field, cursor, i);
			}
		}

		return model;
	}

}
