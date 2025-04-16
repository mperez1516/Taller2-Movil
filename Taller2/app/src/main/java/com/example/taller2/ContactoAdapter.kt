package com.example.taller2

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView

class ContactoAdapter(context: Context, private val contactos: List<String>) :
    ArrayAdapter<String>(context, 0, contactos) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val itemView = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_contacto, parent, false)

        val imgContacto = itemView.findViewById<ImageView>(R.id.imgContacto)
        val txtContacto = itemView.findViewById<TextView>(R.id.txtContacto)

        txtContacto.text = "${position + 1}. ${contactos[position]}"
        imgContacto.setImageResource(R.drawable.ic_contactos)

        return itemView
    }
}
