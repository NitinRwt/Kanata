package app.kanata.settings

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import app.kanata.R
import app.kanata.databinding.ActivitySourcesBinding
import app.kanata.databinding.DialogSourceEditBinding
import app.kanata.databinding.ItemSourceRowBinding
import app.kanata.initActivity
import app.kanata.parsers.AnimeSources
import app.kanata.parsers.BuiltInSource
import app.kanata.parsers.CustomSource
import app.kanata.parsers.SiteType
import app.kanata.parsers.SourceRegistry
import app.kanata.statusBarHeight
import app.kanata.toastString
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Manage streaming sources: change the link of a built-in source when its site moves, or add other sites
 * of a type Kanata can read. Every link is tested before it is saved.
 */
class SourcesActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySourcesBinding
    private val types = SiteType.values()
    private val typeLabels by lazy { types.map { "${it.label} (like ${it.example})" } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySourcesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initActivity(this)

        binding.sourcesClose.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin += statusBarHeight }
        binding.sourcesClose.setOnClickListener { finish() }
        binding.sourcesAdd.setOnClickListener { editCustom(null) }
        render()
    }

    private fun render() {
        binding.sourcesBuiltIn.removeAllViews()
        SourceRegistry.builtIns.forEach { source ->
            val type = source.type.label + if (SourceRegistry.isChanged(source)) " · ${getString(R.string.link_changed)}" else ""
            row(binding.sourcesBuiltIn, source.name, SourceRegistry.host(source), type) { editBuiltIn(source) }
        }
        val customs = SourceRegistry.customs()
        binding.sourcesCustom.removeAllViews()
        customs.forEachIndexed { i, source ->
            row(binding.sourcesCustom, source.name, source.url, source.type.label) { editCustom(i) }
        }
        binding.sourcesCustomEmpty.visibility = if (customs.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun row(parent: LinearLayout, name: String, link: String, type: String, onClick: () -> Unit) {
        val row = ItemSourceRowBinding.inflate(layoutInflater, parent, false)
        row.sourceRowName.text = name
        row.sourceRowLink.text = link
        row.sourceRowType.text = type
        row.root.setOnClickListener { onClick() }
        parent.addView(row.root)
    }

    private fun sourcesChanged() {
        AnimeSources.reload()
        render()
    }

    private fun editBuiltIn(source: BuiltInSource) = openEditor(
        title = "${getString(R.string.edit_source_link)} · ${source.name}",
        name = source.name, url = SourceRegistry.host(source), type = source.type, fixedNameAndType = true,
        removeLabel = if (SourceRegistry.isChanged(source)) getString(R.string.reset) else null,
        onRemove = {
            SourceRegistry.setHost(source, null)
            sourcesChanged()
        },
    ) { _, host, _ ->
        SourceRegistry.setHost(source, host)
        sourcesChanged()
        toastString("${source.name} now uses $host")
    }

    /** [index] of the custom source to edit, or null to add one */
    private fun editCustom(index: Int?) {
        val existing = index?.let { SourceRegistry.customs().getOrNull(it) }
        openEditor(
            title = existing?.name ?: getString(R.string.add_source),
            name = existing?.name, url = existing?.url, type = existing?.type, fixedNameAndType = false,
            removeLabel = if (existing != null) getString(R.string.delete) else null,
            onRemove = {
                SourceRegistry.saveCustoms(SourceRegistry.customs().filterIndexed { i, _ -> i != index })
                sourcesChanged()
            },
        ) { name, host, type ->
            val updated = CustomSource(name, host, type)
            // Editing replaces the source; adding a site that's already listed replaces that entry too
            val others = SourceRegistry.customs().filterIndexed { i, s -> i != index && s.saveName != updated.saveName }
            SourceRegistry.saveCustoms(others + updated)
            sourcesChanged()
            toastString("$name added to your sources")
        }
    }

    private fun openEditor(
        title: String,
        name: String?,
        url: String?,
        type: SiteType?,
        fixedNameAndType: Boolean,
        removeLabel: String?,
        onRemove: () -> Unit,
        onSave: (name: String, host: String, type: SiteType) -> Unit,
    ) {
        val d = DialogSourceEditBinding.inflate(layoutInflater)
        d.sourceDialogTitle.text = title
        d.sourceName.setText(name)
        d.sourceNameLayout.isEnabled = !fixedNameAndType
        d.sourceUrl.setText(url)
        d.sourceType.setAdapter(ArrayAdapter(this, R.layout.item_dropdown, typeLabels))
        var chosen: SiteType? = type
        chosen?.let { d.sourceType.setText(typeLabels[it.ordinal], false) }
        d.sourceTypeLayout.isEnabled = !fixedNameAndType
        d.sourceType.setOnItemClickListener { _, _, position, _ -> chosen = types[position] }
        if (removeLabel != null) {
            d.sourceRemove.visibility = View.VISIBLE
            d.sourceRemove.text = removeLabel
        }

        val dialog = AlertDialog.Builder(this, R.style.DialogTheme).setView(d.root).show()
        // DialogTheme wraps the content; give the form a steady width so long test messages don't resize it
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.9).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)

        fun status(text: String, ok: Boolean?) {
            d.sourceStatus.text = text
            d.sourceStatus.setTextColor(
                when (ok) {
                    true -> MaterialColors.getColor(d.sourceStatus, R.attr.colorSecondary)
                    false -> ContextCompat.getColor(this, R.color.fav)
                    null -> MaterialColors.getColor(d.sourceStatus, R.attr.colorOnSurface)
                }
            )
        }

        fun busy(on: Boolean) {
            d.sourceTest.isEnabled = !on
            d.sourceSave.isEnabled = !on
        }

        /** Detects the site type if needed and tests the link. Returns the cleaned link when it works */
        suspend fun check(): String? {
            val host = SourceRegistry.normalize(d.sourceUrl.text.toString())
            if (host == null) {
                status("Enter a website link, like hianime.to", false)
                return null
            }
            d.sourceUrl.setText(host)
            var siteType = chosen
            if (siteType == null) {
                status("Detecting what kind of site this is…", null)
                siteType = withContext(Dispatchers.IO) { runCatching { SourceRegistry.detect(host) }.getOrNull() }
                if (siteType == null) {
                    status("Couldn't recognise this site. If you know its type, choose it above and test again.", false)
                    return null
                }
                chosen = siteType
                d.sourceType.setText(typeLabels[siteType.ordinal], false)
            }
            if (d.sourceName.text.isNullOrBlank()) d.sourceName.setText(host.toHttpUrlOrNull()?.host)
            status("Testing ${siteType.label} at $host…", null)
            val result = withContext(Dispatchers.IO) { SourceRegistry.test(siteType.create(host, "Test", "source_test")) }
            status(result.message, result.ok)
            return if (result.ok) host else null
        }

        fun save(host: String) {
            val siteType = chosen ?: return
            val finalName = d.sourceName.text?.toString()?.trim().takeUnless { it.isNullOrEmpty() }
                ?: host.toHttpUrlOrNull()?.host ?: host
            onSave(finalName, host, siteType)
            dialog.dismiss()
        }

        // A failed test turns Save into "Save anyway", in case the site is only down for a moment
        var saveAnyway = false
        d.sourceUrl.doAfterTextChanged {
            if (saveAnyway) {
                saveAnyway = false
                d.sourceSave.setText(R.string.save)
            }
        }
        d.sourceTest.setOnClickListener {
            lifecycleScope.launch {
                busy(true)
                check()
                busy(false)
            }
        }
        d.sourceSave.setOnClickListener {
            lifecycleScope.launch {
                busy(true)
                val typed = SourceRegistry.normalize(d.sourceUrl.text.toString())
                if (saveAnyway && typed != null && chosen != null) save(typed)
                else {
                    val host = check()
                    if (host != null) save(host)
                    else if (typed != null && chosen != null) {
                        saveAnyway = true
                        d.sourceSave.setText(R.string.save_anyway)
                    }
                }
                busy(false)
            }
        }
        d.sourceRemove.setOnClickListener {
            onRemove()
            dialog.dismiss()
        }
    }
}
