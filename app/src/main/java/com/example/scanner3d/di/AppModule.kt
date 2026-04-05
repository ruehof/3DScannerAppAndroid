package com.example.scanner3d.di

import com.example.scanner3d.data.repository.ScanSettingsRepositoryImpl
import com.example.scanner3d.data.repository.TurntableRepositoryImpl
import com.example.scanner3d.domain.repository.ScanSettingsRepository
import com.example.scanner3d.domain.repository.TurntableRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt-Modul: Bindet Repository-Interfaces an ihre Implementierungen.
 * SingletonComponent: Repositories leben so lange wie die App.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindTurntableRepository(
        impl: TurntableRepositoryImpl
    ): TurntableRepository

    @Binds
    @Singleton
    abstract fun bindScanSettingsRepository(
        impl: ScanSettingsRepositoryImpl
    ): ScanSettingsRepository
}
