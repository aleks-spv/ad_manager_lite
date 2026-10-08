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

import com.adm.lite.ldap.LdapErrorMapper
import com.adm.lite.ldap.LdapExecutor
import com.adm.lite.model.AdError
import com.unboundid.ldap.sdk.LDAPException
import com.unboundid.ldap.sdk.Modification
import com.unboundid.ldap.sdk.ModificationType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UnlockAccountImpl(
    private val executor: LdapExecutor,
    private val errors: LdapErrorMapper
) : UnlockAccountUseCase {
    override suspend fun unlock(userDn: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                executor.execute { conn ->
                    // lockoutTime=0 clears the lockout; badPwdCount auto-resets after that
                    conn.modify(userDn, Modification(ModificationType.REPLACE, "lockoutTime", "0"))
                }
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: LDAPException) {
                Result.failure(errors.map(e))
            } catch (e: AdError) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(
                    AdError(AdError.Kind.UNKNOWN, null, null, e.message ?: "Unlock failed")
                )
            }
        }
    }
}
