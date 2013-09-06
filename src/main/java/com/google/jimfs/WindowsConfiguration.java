/*
 * Copyright 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.jimfs;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.jimfs.internal.attribute.UserLookupService.createUserPrincipal;

import com.google.common.base.Ascii;
import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.jimfs.internal.JimfsFileSystem;
import com.google.jimfs.internal.attribute.AclAttributeProvider;
import com.google.jimfs.internal.attribute.AttributeProvider;
import com.google.jimfs.internal.attribute.BasicAttributeProvider;
import com.google.jimfs.internal.attribute.DosAttributeProvider;
import com.google.jimfs.internal.attribute.OwnerAttributeProvider;
import com.google.jimfs.internal.attribute.UserDefinedAttributeProvider;
import com.google.jimfs.internal.path.JimfsPath;
import com.google.jimfs.internal.path.Name;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.util.List;

/**
 * @author Colin Decker
 */
public final class WindowsConfiguration extends JimfsConfiguration {

  private static final Joiner JOINER = Joiner.on('\\');
  private static final CharMatcher SEPARATOR_MATCHER = CharMatcher.anyOf("\\/");
  private static final Splitter SPLITTER = Splitter.on(SEPARATOR_MATCHER).omitEmptyStrings();

  private final String workingDirectory;
  private final String defaultUser;
  private final List<AclEntry> defaultAclEntries;
  private final ImmutableSet<String> roots;

  public WindowsConfiguration() {
    this("C:\\");
  }

  public WindowsConfiguration(String... roots) {
    this("C:\\work", "user", ImmutableList.<AclEntry>of(), roots);
  }

  public WindowsConfiguration(String workingDirectory, String defaultUser,
      List<AclEntry> defaultAclEntries, String... roots) {
    this.workingDirectory = checkNotNull(workingDirectory);
    this.defaultUser = checkNotNull(defaultUser);
    this.defaultAclEntries = ImmutableList.copyOf(defaultAclEntries);
    this.roots = ImmutableSet.copyOf(roots);
  }

  @Override
  public String getSeparator() {
    return "\\";
  }

  @Override
  protected Iterable<String> getAlternateSeparators() {
    return ImmutableSet.of("/");
  }

  @Override
  public Iterable<String> getRoots() {
    return roots;
  }

  @Override
  public String getWorkingDirectory() {
    return workingDirectory;
  }

  @Override
  public boolean isHidden(Path path) throws IOException {
    return (boolean) Files.getAttribute(path, "dos:hidden");
  }

  @Override
  public Iterable<AttributeProvider> getAttributeProviders() {
    BasicAttributeProvider basic = new BasicAttributeProvider();
    OwnerAttributeProvider owner = new OwnerAttributeProvider(createUserPrincipal(defaultUser));
    DosAttributeProvider dos = new DosAttributeProvider(basic);
    AclAttributeProvider acl = new AclAttributeProvider(owner, defaultAclEntries);
    UserDefinedAttributeProvider user = new UserDefinedAttributeProvider();
    return ImmutableList.<AttributeProvider>of(basic, owner, dos, acl, user);
  }

  @Override
  public Name createName(String name, boolean root) {
    if (root) {
      Name canonical = defaultCreateName(Ascii.toUpperCase(name) + "\\");
      return Name.create(name, canonical);
    }
    return defaultCreateName(name);
  }

  @Override
  public JimfsPath parsePath(JimfsFileSystem fileSystem, List<String> path) {
    String joined = JOINER.join(path);
    Name root = null;
    if (joined.length() >= 2
        && CharMatcher.JAVA_LETTER.matches(joined.charAt(0))
        && joined.charAt(1) == ':') {
      root = createName(joined.substring(0, 2), true);
      joined = joined.substring(2);
    }

    Iterable<String> split = SPLITTER.split(joined);
    Iterable<Name> result = Iterables.concat(Optional.fromNullable(root).asSet(), toNames(split));
    return JimfsPath.create(fileSystem, result, root != null);
  }
}