package com.truelock.enigma.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.truelock.enigma.R
import com.truelock.enigma.databinding.ActivityProfileManagerBinding
import com.truelock.enigma.profiles.KeyProfile
import com.truelock.enigma.profiles.KeyProfileFactory
import com.truelock.enigma.profiles.ProfileLifecycleService
import com.truelock.enigma.storage.FileKeyProfileRepository
import com.truelock.enigma.storage.ProfileKeyVault
import com.truelock.enigma.storage.SecureProfileStore

class ProfileManagerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProfileManagerBinding
    private lateinit var secureProfileStore: SecureProfileStore
    private val profileFactory = KeyProfileFactory()
    private var renderedProfiles: List<KeyProfile> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        secureProfileStore = SecureProfileStore(
            repository = FileKeyProfileRepository(applicationContext),
            keyVault = ProfileKeyVault(),
        )

        binding.createProfileButton.setOnClickListener {
            upsertProfileFromInputs()
        }
        binding.resetFormButton.setOnClickListener {
            resetForm(getString(R.string.profile_status_form_reset))
        }
        binding.clearProfilesButton.setOnClickListener {
            secureProfileStore.clearAll()
            resetForm(getString(R.string.profile_status_all_cleared))
        }
        binding.renewProfileButton.setOnClickListener {
            renewSelectedProfile()
        }
        binding.archiveProfileButton.setOnClickListener {
            archiveSelectedProfile()
        }
        binding.deleteProfileButton.setOnClickListener {
            deleteSelectedProfile()
        }
        binding.profileListView.setOnItemClickListener { _, _, position, _ ->
            val profile = renderedProfiles.getOrNull(position) ?: return@setOnItemClickListener
            bindSelectedProfile(profile)
            binding.statusText.text = getString(R.string.profile_status_selected, profile.title)
        }

        seedDefaults()
        renderProfiles(getString(R.string.profile_status_ready))
    }

    private fun seedDefaults() {
        if (binding.titleInput.text.isNullOrBlank()) {
            binding.titleInput.setText(getString(R.string.profile_default_title))
        }
        if (binding.appPackageInput.text.isNullOrBlank()) {
            binding.appPackageInput.setText(getString(R.string.profile_default_package))
        }
        if (binding.peerHintInput.text.isNullOrBlank()) {
            binding.peerHintInput.setText(getString(R.string.profile_default_peer))
        }
        if (binding.emojiSequenceInput.text.isNullOrBlank()) {
            binding.emojiSequenceInput.setText(getString(R.string.profile_default_emoji_sequence))
        }
    }

    private fun upsertProfileFromInputs() {
        val title = binding.titleInput.text?.toString()?.trim().orEmpty()
        val appPackage = binding.appPackageInput.text?.toString()?.trim().orEmpty()
        val peerHint = binding.peerHintInput.text?.toString()?.trim().orEmpty()
        val emojiRaw = binding.emojiSequenceInput.text?.toString()?.trim().orEmpty()
        val emojis = emojiRaw
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        if (title.isBlank()) {
            renderProfiles(getString(R.string.profile_error_title_required))
            return
        }
        val existing = findSelectedProfileOrNull()
        if (existing == null && emojis.size != 5) {
            renderProfiles(getString(R.string.profile_error_exactly_five_emoji))
            return
        }
        if (existing != null && emojiRaw.isNotBlank() && emojis.size != 5) {
            renderProfiles(getString(R.string.profile_error_exactly_five_emoji))
            return
        }

        val profileToSave = if (existing != null && emojiRaw.isBlank()) {
            existing.copy(
                title = title,
                appPackage = appPackage.ifBlank { null },
                peerHint = peerHint.ifBlank { null },
            )
        } else {
            val result = profileFactory.createFromEmojiSequence(
                title = title,
                emojis = emojis,
                appPackage = appPackage.ifBlank { null },
                peerHint = peerHint.ifBlank { null },
            )

            if (existing != null) {
                secureProfileStore.saveProfile(
                    result.profile.copy(
                        id = existing.id,
                        profileVersion = existing.profileVersion + 1,
                        createdAt = existing.createdAt,
                        lastUsedAt = existing.lastUsedAt,
                    ),
                    result.profileKey,
                )
                binding.selectedProfileIdInput.setText(existing.id)
                binding.createProfileButton.setText(R.string.save_profile)
                renderProfiles(getString(R.string.profile_status_updated, title))
                return
            }

            secureProfileStore.saveProfile(result.profile, result.profileKey)
            binding.selectedProfileIdInput.setText(result.profile.id)
            binding.createProfileButton.setText(R.string.save_profile)
            renderProfiles(getString(R.string.profile_status_created, result.profile.title))
            return
        }

        secureProfileStore.saveProfile(profileToSave)
        binding.selectedProfileIdInput.setText(profileToSave.id)
        binding.createProfileButton.setText(R.string.save_profile)
        renderProfiles(getString(R.string.profile_status_updated, profileToSave.title))
    }

    private fun renewSelectedProfile() {
        val profile = findSelectedProfile() ?: return
        val renewed = ProfileLifecycleService.renew(profile)
        secureProfileStore.saveProfile(renewed)
        renderProfiles(getString(R.string.profile_status_renewed, renewed.title))
    }

    private fun archiveSelectedProfile() {
        val profile = findSelectedProfile() ?: return
        val archived = ProfileLifecycleService.archive(profile)
        secureProfileStore.saveProfile(archived)
        renderProfiles(getString(R.string.profile_status_archived_message, archived.title))
    }

    private fun deleteSelectedProfile() {
        val profile = findSelectedProfile() ?: return
        secureProfileStore.deleteProfile(profile.id)
        resetForm(getString(R.string.profile_status_deleted, profile.title))
    }

    private fun bindSelectedProfile(profile: KeyProfile) {
        binding.selectedProfileIdInput.setText(profile.id)
        binding.titleInput.setText(profile.title)
        binding.appPackageInput.setText(profile.appPackage.orEmpty())
        binding.peerHintInput.setText(profile.peerHint.orEmpty())
        binding.emojiSequenceInput.setText("")
        binding.emojiSequenceInput.hint = getString(R.string.profile_emoji_sequence_edit_hint)
        binding.createProfileButton.setText(R.string.save_profile)
        binding.selectedProfileLabel.text = getString(R.string.profile_manager_selected_format, profile.title)
    }

    private fun resetForm(status: String) {
        binding.selectedProfileIdInput.setText("")
        binding.titleInput.setText(getString(R.string.profile_default_title))
        binding.appPackageInput.setText(getString(R.string.profile_default_package))
        binding.peerHintInput.setText(getString(R.string.profile_default_peer))
        binding.emojiSequenceInput.setText(getString(R.string.profile_default_emoji_sequence))
        binding.emojiSequenceInput.hint = getString(R.string.profile_emoji_sequence_hint)
        binding.createProfileButton.setText(R.string.create_profile)
        binding.selectedProfileLabel.setText(R.string.profile_manager_selected_placeholder)
        renderProfiles(status)
    }

    private fun findSelectedProfile(): KeyProfile? {
        val id = binding.selectedProfileIdInput.text?.toString()?.trim().orEmpty()
        if (id.isBlank()) {
            renderProfiles(getString(R.string.profile_error_id_required))
            return null
        }

        return secureProfileStore.listProfiles().firstOrNull { it.id == id }
            ?: run {
                renderProfiles(getString(R.string.profile_error_not_found, id))
                null
            }
    }

    private fun findSelectedProfileOrNull(): KeyProfile? {
        val id = binding.selectedProfileIdInput.text?.toString()?.trim().orEmpty()
        if (id.isBlank()) return null
        return secureProfileStore.listProfiles().firstOrNull { it.id == id }
    }

    private fun renderProfiles(status: String) {
        val profiles = secureProfileStore.listProfiles()
        renderedProfiles = profiles
        binding.statusText.text = status
        binding.profileCountText.text = getString(R.string.profile_count_format, profiles.size)

        val listItems = if (profiles.isEmpty()) {
            listOf(getString(R.string.profile_list_placeholder))
        } else {
            profiles.map { profile -> formatProfileListItem(profile) }
        }

        binding.profileListView.adapter = ArrayAdapter(
            this,
            R.layout.item_profile_list,
            listItems,
        )

        val selectedId = binding.selectedProfileIdInput.text?.toString()?.trim().orEmpty()
        val selectedIndex = profiles.indexOfFirst { it.id == selectedId }
        if (selectedIndex >= 0) {
            binding.profileListView.setItemChecked(selectedIndex, true)
            binding.selectedProfileLabel.text = getString(
                R.string.profile_manager_selected_format,
                profiles[selectedIndex].title,
            )
        } else {
            binding.profileListView.clearChoices()
            binding.selectedProfileLabel.setText(R.string.profile_manager_selected_placeholder)
        }
    }
}
