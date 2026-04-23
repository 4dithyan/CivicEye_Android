package com.civiceye.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.civiceye.databinding.ActivityAuthBinding
import com.civiceye.ui.main.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AuthActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityAuthBinding
    private val viewModel: AuthViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Check if already logged in (fetch profile first to determine role)
        if (viewModel.isLoggedIn) {
            viewModel.checkSession()
        }
        
        setupClickListeners()
        observeState()
    }
    
    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()
            
            if (validateInput(email, password)) {
                viewModel.login(email, password)
            }
        }
        
        binding.tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
        
        binding.tvForgotPassword.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            if (email.isNotEmpty()) {
                viewModel.sendPasswordReset(email)
            } else {
                binding.tilEmail.error = "Enter your email first"
            }
        }
    }
    
    private fun observeState() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                binding.progressLogin.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                binding.btnLogin.visibility = if (state.isLoading) View.INVISIBLE else View.VISIBLE
                
                state.error?.let { error ->
                    Toast.makeText(this@AuthActivity, error, Toast.LENGTH_LONG).show()
                    viewModel.clearError()
                }
                
                if (state.isLoggedIn) {
                    if (state.user?.isStaff() == true) {
                        navigateToStaff()
                    } else {
                        navigateToMain()
                    }
                }
                
                if (state.passwordResetSent) {
                    Toast.makeText(this@AuthActivity, "Password reset email sent!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun validateInput(email: String, password: String): Boolean {
        var isValid = true
        
        if (email.isEmpty()) {
            binding.tilEmail.error = getString(com.civiceye.R.string.error_empty_email)
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = getString(com.civiceye.R.string.error_invalid_email)
            isValid = false
        } else {
            binding.tilEmail.error = null
        }
        
        if (password.isEmpty()) {
            binding.tilPassword.error = getString(com.civiceye.R.string.error_empty_password)
            isValid = false
        } else if (password.length < 6) {
            binding.tilPassword.error = getString(com.civiceye.R.string.error_short_password)
            isValid = false
        } else {
            binding.tilPassword.error = null
        }
        
        return isValid
    }
    
    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
    
    private fun navigateToStaff() {
        startActivity(Intent(this, com.civiceye.ui.staff.StaffActivity::class.java))
        finish()
    }
}
