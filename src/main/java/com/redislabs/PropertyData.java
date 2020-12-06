package com.redislabs;

import java.io.Serializable;

import org.hibernate.type.AbstractStandardBasicType;
import org.hibernate.type.Type;

public class PropertyData implements Serializable{

  /**
   * 
   */
  private static final long serialVersionUID = 1L;
  private String name;
  private String columnName;
  private boolean isNullable;
  private Type type;
  private AbstractStandardBasicType<?> adType;
  
  public PropertyData(String name, String columnName, Type type, boolean isNullable) {
      this.name = name;
      this.type = type;
      this.columnName = columnName;
      this.isNullable = isNullable;
      
      if(this.type instanceof AbstractStandardBasicType<?>) {
        this.adType = (AbstractStandardBasicType<?>)this.type;
      }
  }
  
  public Object convert(String val) {
    if(adType == null) {
      // best effort, keep it as string.
      return val;
    }
    return adType.fromString(val);
  }

  public String getName() {
    return name;
  }

  public String getColumnName() {
    return columnName;
  }

  public String getType() {
    return type.getName();
  }
  
  public boolean isNullable() {
    return isNullable;
  }

  @Override
  public String toString() {
    return String.format("%s -> %s (type: %s, mandatory: %b)", getName(), getColumnName(), getType(), !isNullable());
  }
  
  
  
}