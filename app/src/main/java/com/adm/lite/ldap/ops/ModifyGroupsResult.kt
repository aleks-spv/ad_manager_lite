/*
 * Copyright 2026 Alexandr <aleks.spv@gmail.com>
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

package com.adm.lite.ldap.ops

import com.adm.lite.model.AdError

/**
 * Result of a partial-failure-tolerant group modification.
 *
 * [appliedDns] contains the group DNs that were successfully modified.
 * [failures] contains pairs of (group DN, error) for groups that could not be modified.
 *
 * A call may return a [ModifyGroupsResult] with a non-empty [failures] list
 * AND a non-empty [appliedDns] list — this is a partial success.
 * The caller should always check both lists to determine how many groups were updated.
 */
data class ModifyGroupsResult(
    val appliedDns: List<String>,
    val failures: List<Pair<String, AdError>>
)
