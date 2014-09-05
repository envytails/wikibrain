//   BuildProperties.java
//   Java Spatial Index Library
//   Copyright (C) 2012 Aled Morris <aled@users.sourceforge.net>
//  
//  This library is free software; you can redistribute it and/or
//  modify it under the terms of the GNU Lesser General Public
//  License as published by the Free Software Foundation; either
//  version 2.1 of the License, or (at your option) any later version.
//  
//  This library is distributed in the hope that it will be useful,
//  but WITHOUT ANY WARRANTY; without even the implied warranty of
//  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
//  Lesser General Public License for more details.
//  
//  You should have received a copy of the GNU Lesser General Public
//  License along with this library; if not, write to the Free Software
//  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package net.sf.jsi;

import java.io.IOException;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Allows build properties to be retrieved at runtime. Currently, version and
 * scmRevisionId are implemented.
 */
public class BuildProperties {
  private static final Logger log = LoggerFactory.getLogger(BuildProperties.class);  
  private static final BuildProperties instance = new BuildProperties();
  
  private String version = null;
  private String scmRevisionId = null;
  
  private BuildProperties() {
    Properties p = new Properties();
    try {
      p.load(getClass().getClassLoader().getResourceAsStream("build.properties"));
      version = p.getProperty("version", "");
      scmRevisionId = p.getProperty("scmRevisionId", "");
    } catch (IOException e) {
      log.warn("Unable to read from build.properties");
    }
  }
  
  /**
   * Version number as specified in pom.xml
   */
  public static String getVersion() {
    return instance.version;
  }
  
  
  /**
   * SCM revision ID. This is the git commit ID.
   */
  public static String getScmRevisionId() {
    return instance.scmRevisionId;
  }
}
