<!--
Copyright (c) 2019, Amazon.com, Inc. or its affiliates. All Rights Reserved.
DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.

This code is free software; you can redistribute it and/or modify it
under the terms of the GNU General Public License version 2 only, as
published by the Free Software Foundation. Amazon designates this
particular file as subject to the "Classpath" exception as provided
by Oracle in the LICENSE file that accompanied this code.

This code is distributed in the hope that it will be useful, but WITHOUT
ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
version 2 for more details (a copy is included in the LICENSE file that
accompanied this code).

You should have received a copy of the GNU General Public License version
2 along with this work; if not, write to the Free Software Foundation,
Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
-->
Contributing to Amazon Corretto
================================

You have found a bug or you have an idea for a cool new feature? Contributing code is a great way to give something back to
the open source community. Before you dig right into the code there are a few guidelines that we need contributors to
follow so that we can have a chance of keeping on top of things.

Getting Started
---------------

+ Make sure you have a [GitHub account](https://github.com/signup/free).
+ Submit a [GitHub Issue](https://github.com/corretto/corretto-8/issues) for your issue, assuming one does not already exist.
  + Clearly describe the issue including steps to reproduce when it is a bug.
  + Make sure you fill in the earliest version that you know has the issue.
+ [fork](https://help.github.com/articles/fork-a-repo/) your repository and check out your forked repository.

Making Changes
--------------

+ Create a _topic branch_ for your isolated work.
  * Usually you should base your branch on the `master` branch.
  * A good topic branch name can be the JIRA bug id plus a keyword, e.g. `issue-35-InputStream`.
  * If you have submitted multiple JIRA issues, try to maintain separate branches and pull requests.
+ Make commits of logical units.
  * Make sure your commit messages are meaningful and in the proper format. Your commit message should contain the key of the JIRA issue.
  * e.g. `#35: Close input stream earlier`
+ Respect the original code style:
  + Only use spaces for indentation.
  + Create minimal diffs - disable _On Save_ actions like _Reformat Source Code_ or _Organize Imports_. If you feel the source code should be reformatted create a separate PR for this change first.
  + Check for unnecessary whitespace with `git diff` -- check before committing.
+ Make sure you have added the necessary tests for your changes.
+ Run all the tests with `mvn clean verify` to assure nothing else was accidentally broken.

Making Trivial Changes
----------------------

The GitHub Issues are used to generate the changelog for the next release.

For changes of a trivial nature to comments and documentation, it is not always necessary to create a new issue in GitHub.
In this case, it is appropriate to start the first line of a commit with '(doc)' instead of a ticket number.


Submitting Changes
------------------

+ Push your changes to a topic branch in your fork of the repository.
+ Submit a _Pull Request_ to the corresponding repository in the `apache` organization.
  * Verify _Files Changed_ shows only your intended changes and does not
  include additional files like `target/*.class`
+ Update your JIRA ticket and include a link to the pull request in the ticket.

If you prefer to not use GitHub, then you can instead use
`git format-patch` (or `svn diff`) and attach the patch file to the JIRA issue.


Additional Resources
--------------------

+ [General GitHub documentation](https://help.github.com/)
+ [GitHub pull request documentation](https://help.github.com/articles/creating-a-pull-request/)
