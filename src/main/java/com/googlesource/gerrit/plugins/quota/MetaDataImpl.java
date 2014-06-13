package com.googlesource.gerrit.plugins.quota;

import com.google.gerrit.extensions.events.UsageDataPublishedListener.MetaData;

class MetaDataImpl implements MetaData {

  private String name;
  private String unitName;
  private String unitSymbol;
  private String description;

  public MetaDataImpl(String name, String unitName, String unitSymbol, String description) {
    this.name = name;
    this.unitName = unitName;
    this.unitSymbol = unitSymbol;
    this.description = description;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getUnitName() {
    return unitName;
  }

  @Override
  public String getUnitSymbol() {
    return unitSymbol;
  }

  @Override
  public String getDescription() {
    return description;
  }
}
