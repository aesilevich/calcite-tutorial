<#--
// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements.  See the NOTICE file distributed with
// this work for additional information regarding copyright ownership.
// The ASF licenses this file to you under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with
// the License.  You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
-->


// Parses DEALLOCATE statement
SqlNode SqlDeallocate() :
{
  final Span s;
  final SqlIdentifier id;
  boolean prepare = false;
}
{
  <DEALLOCATE> { s = span(); }

  [ <PREPARE> { prepare = true; } ]

  (
    <ALL> {
      return DeallocateStatements.deallocateAll(s.end(this), prepare);
    }
  |
    id = SimpleIdentifier() {
      return DeallocateStatements.deallocate(s.end(this), prepare, id);
    }
  )
}
