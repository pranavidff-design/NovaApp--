package com.nova.assistant

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.nova.assistant.memory.MemoryEntity
import com.nova.assistant.memory.MemoryManager
import kotlinx.coroutines.launch

class MemoryActivity : AppCompatActivity() {

    private lateinit var memory: MemoryManager
    private lateinit var listContainer: LinearLayout
    private lateinit var enabledSwitch: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_memory)

        memory = MemoryManager(this)
        listContainer = findViewById(R.id.memoryListContainer)
        enabledSwitch = findViewById(R.id.memoryEnabledSwitch)

        enabledSwitch.isChecked = memory.memoryEnabled
        enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            memory.memoryEnabled = isChecked
        }

        findViewById<Button>(R.id.clearAllButton).setOnClickListener {
            lifecycleScope.launch {
                memory.clearAll()
                refreshList()
            }
        }

        refreshList()
    }

    private fun refreshList() {
        lifecycleScope.launch {
            val facts = memory.recallAll()
            listContainer.removeAllViews()
            if (facts.isEmpty()) {
                val empty = TextView(this@MemoryActivity).apply {
                    text = "Nothing saved yet."
                    setTextColor(getColor(R.color.nova_text_dim))
                }
                listContainer.addView(empty)
                return@launch
            }
            for (fact in facts) {
                addMemoryRow(fact)
            }
        }
    }

    private fun addMemoryRow(entity: MemoryEntity) {
        val row = LayoutInflater.from(this).inflate(R.layout.item_memory, listContainer, false)
        row.findViewById<TextView>(R.id.memoryFactText).text = entity.fact
        row.findViewById<Button>(R.id.deleteButton).setOnClickListener {
            lifecycleScope.launch {
                memory.forget(entity)
                refreshList()
            }
        }
        listContainer.addView(row)
    }
}
